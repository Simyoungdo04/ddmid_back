package com.kh.midpoint.room.model.service;

import com.kh.midpoint.common.exception.InvalidStateException;
import com.kh.midpoint.common.exception.NotFoundException;
import com.kh.midpoint.external.kakao.TransitLegDto;
import com.kh.midpoint.external.tmap.RoutePointDto;
import com.kh.midpoint.gameparticipant.model.service.Game;
import com.kh.midpoint.midpoint.service.MidpointFinder;
import com.kh.midpoint.participant.model.dao.ParticipantMapper;
import com.kh.midpoint.participant.model.dto.ParticipantDto;
import com.kh.midpoint.participant.model.dto.ParticipantResponse;
import com.kh.midpoint.restaurant.model.dto.RestaurantDto;
import com.kh.midpoint.restaurant.model.service.RestaurantService;
import com.kh.midpoint.room.model.dao.RoomMapper;
import com.kh.midpoint.room.model.dto.RoomDto;
import com.kh.midpoint.room.model.dto.RoomResponse;
import com.kh.midpoint.room.model.vo.Room;
import com.kh.midpoint.roomresult.model.service.RoomResultService;
import com.kh.midpoint.route.model.dao.ParticipantRouteMapper;
import com.kh.midpoint.route.model.dto.ParticipantRouteDto;
import com.kh.midpoint.route.service.RouteFiller;
import com.kh.midpoint.selection.model.dao.SelectionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 방(Room) 책임만 담당한다 - 방 단계 전환의 "순서"와, 여러 테이블(ROOM/PARTICIPANT/
// SELECTION/ROOM_RESULT/PARTICIPANT_ROUTE)을 조합해서 화면에 내려줄 응답을 만드는 일을 한다.
// 실제 중간지점 계산/경로 조회는 MidpointFinder/RouteFiller(지도 책임)에게, 만장일치가
// 아닐 때 승자를 정하는 일은 Game(gameparticipant 책임)에게 맡긴다.
@Service
public class RoomService {

	private static final Logger log = LoggerFactory.getLogger(RoomService.class);

	private final RoomMapper roomMapper;
	private final ParticipantMapper participantMapper;
	private final ParticipantRouteMapper participantRouteMapper;
	private final SelectionMapper selectionMapper;
	private final RestaurantService restaurantService;
	private final RoomResultService roomResultService;
	private final Game game;
	private final MidpointFinder midpointFinder;
	private final RouteFiller routeFiller;
	private final StringRedisTemplate redisTemplate;

	public RoomService(
			RoomMapper roomMapper, ParticipantMapper participantMapper, ParticipantRouteMapper participantRouteMapper,
			SelectionMapper selectionMapper, RestaurantService restaurantService, RoomResultService roomResultService,
			Game game, MidpointFinder midpointFinder, RouteFiller routeFiller, StringRedisTemplate redisTemplate
	) {
		this.roomMapper = roomMapper;
		this.participantMapper = participantMapper;
		this.participantRouteMapper = participantRouteMapper;
		this.selectionMapper = selectionMapper;
		this.restaurantService = restaurantService;
		this.roomResultService = roomResultService;
		this.game = game;
		this.midpointFinder = midpointFinder;
		this.routeFiller = routeFiller;
		this.redisTemplate = redisTemplate;
	}

	// 중간지점을 못 찾아서(NotFoundException) 방장이 "위치 재설정하기"를 누르면,
	// 방 안 전원이 폴링으로 이 상태를 보고 자기 위치 수정 화면으로 자동 전환되게 하는 신호.
	// DB에 넣을 정도로 영구적인 데이터가 아니라(다음 중간지점 계산 성공하면 바로 의미
	// 없어짐) Redis에 TTL 걸어서만 보관한다 - 서버가 재시작돼도 최악의 경우 이 신호만
	// 사라질 뿐 방/참여자 데이터에는 영향 없다.
	private static final String RELOCATION_KEY_PREFIX = "room:relocation:";
	private static final Duration RELOCATION_TTL = Duration.ofMinutes(30);

	// 방은 만든 뒤 EXPIRATION_HOURS 동안 아무 활동이 없어도(폴링 GET 요청은 활동으로 안 침)
	// RoomCleanupScheduler가 자동으로 지운다. 아직 "활동 감지 시 연장" 로직은 없고, 생성
	// 시점 기준 고정 타이머다 - 나중에 Redis 기반으로 바꾸기 전까지의 임시 구현.
	private static final int EXPIRATION_HOURS = 3;

	@Transactional
	public RoomResponse createRoom(int headCount) {
		Long roomId = roomMapper.nextRoomId();
		LocalDateTime now = LocalDateTime.now();
		Room room = Room.builder()
				.roomId(roomId)
				.roomUuid(generateRoomUuid())
				.maxParticipants(headCount)
				.stage("WAITING")
				.createdAt(now)
				.expiresAt(now.plusHours(EXPIRATION_HOURS))
				.build();
		roomMapper.insert(room);
		return toResponse(requireRoom(room.getRoomUuid()));
	}

	@Transactional(readOnly = true)
	public RoomResponse getRoom(String roomUuid) {
		return toResponse(requireRoom(roomUuid));
	}

	@Transactional
	public RoomResponse setMode(String roomUuid, String mode) {
		RoomDto room = requireRoom(roomUuid);
		roomMapper.updateMode(room.getRoomId(), mode, "MODE_SELECTED");
		return toResponse(requireRoom(roomUuid));
	}

	// 방장이 미리 고른 이동수단(도보/대중교통) 기준으로 중간지점을 찾는다. 실제 계산은
	// MidpointFinder(지도 책임)에게 맡기고, 여기서는 결과를 방 상태에 반영만 한다.
	@Transactional
	public RoomResponse findMidpoint(String roomUuid) {
		RoomDto room = requireRoom(roomUuid);
		if (room.getMode() == null) {
			throw new InvalidStateException("이동 방법을 먼저 선택해주세요.");
		}

		List<ParticipantDto> participants = participantMapper.findActiveByRoomId(room.getRoomId());
		MidpointFinder.MidpointResult result = midpointFinder.find(participants, room.getMode());
		roomMapper.updateMidpoint(room.getRoomId(), result.point().getLat(), result.point().getLng(), result.source(), "MIDPOINT_FOUND");
		redisTemplate.delete(RELOCATION_KEY_PREFIX + room.getRoomId());
		return toResponse(requireRoom(roomUuid));
	}

	// 방장이 "위치 재설정하기"를 누르면 호출된다 - 방 전체에 재설정 신호를 켜서, 폴링 중인
	// 모든 참여자가 각자 위치 수정 화면으로 자동 전환되게 한다(RoomResponse.needsRelocation).
	@Transactional
	public RoomResponse requestRelocation(String roomUuid) {
		RoomDto room = requireRoom(roomUuid);
		redisTemplate.opsForValue().set(RELOCATION_KEY_PREFIX + room.getRoomId(), "1", RELOCATION_TTL);
		return toResponse(room);
	}

	// 참여자가 식당을 고르면(SelectionService가 처리) 방을 RESOLVING 단계로 넘긴다.
	@Transactional
	public RoomResponse markResolving(String roomUuid) {
		RoomDto room = requireRoom(roomUuid);
		roomMapper.updateStage(room.getRoomId(), "RESOLVING");
		return toResponse(requireRoom(roomUuid));
	}

	// 참여자 전원이 식당을 골라야 확정할 수 있다. 전원 일치하면 그 식당, 하나라도 다르면
	// Game이 한 명을 뽑아 그 사람이 고른 식당으로 확정한다. 이후 참여자별 경로(도보/대중교통)는
	// RouteFiller(지도 책임)에게 맡겨서 채운다.
	@Transactional
	public RoomResponse resolve(String roomUuid) {
		RoomDto room = requireRoom(roomUuid);
		List<ParticipantDto> participants = participantMapper.findActiveByRoomId(room.getRoomId());

		record Choice(ParticipantDto participant, RestaurantDto restaurant) {
		}
		List<Choice> choices = new ArrayList<>();
		for (ParticipantDto participant : participants) {
			selectionMapper.findChosenRestaurantByParticipantId(participant.getParticipantId())
					.ifPresent(restaurant -> choices.add(new Choice(participant, restaurant)));
		}
		if (choices.size() < participants.size()) {
			throw new InvalidStateException("아직 전원이 식당을 고르지 않았습니다.");
		}

		boolean unanimous = choices.stream().map(c -> c.restaurant().getId()).distinct().count() == 1;
		Game.GameResult gameResult = unanimous
				? game.recordUnanimousWinner(room.getRoomId(), choices.get(0).participant())
				: game.pickWinner(room.getRoomId(), choices.stream().map(Choice::participant).toList());

		RestaurantDto winningRestaurant = choices.stream()
				.filter(c -> c.participant().getParticipantId().equals(gameResult.winner().getParticipantId()))
				.findFirst()
				.map(Choice::restaurant)
				.orElseThrow();

		Long restaurantId = restaurantService.ensurePersisted(room.getRoomId(), winningRestaurant);
		roomResultService.record(room.getRoomId(), restaurantId, gameResult.gameParticipantId());

		for (ParticipantDto participant : participants) {
			// RouteFiller 안에서 이미 처리되지만, 혹시 놓친 케이스가 있어도 참여자 한 명
			// 때문에 방 전체가 RESOLVING에 멈추면 안 되므로 한 번 더 감싼다.
			try {
				routeFiller.fillWalkRoute(room.getRoomId(), participant, winningRestaurant);
			} catch (RuntimeException e) {
				// 이 사람만 도보 경로를 못 찾은 것 -> 나머지 결과는 그대로 보여준다. 다만
				// 원인을 알 수 없으면 디버깅이 불가능하니 로그는 남긴다.
				log.warn("참여자 {} 도보 경로 계산 실패", participant.getParticipantId(), e);
			}
			try {
				routeFiller.fillTransitRoute(room.getRoomId(), participant, winningRestaurant);
			} catch (RuntimeException e) {
				log.warn("참여자 {} 대중교통 경로 계산 실패", participant.getParticipantId(), e);
				// 이 사람만 대중교통 경로를 못 찾은 것 -> 나머지 결과는 그대로 보여준다.
			}
		}

		roomMapper.updateStage(room.getRoomId(), "RESOLVED");
		return toResponse(requireRoom(roomUuid));
	}

	@Transactional(readOnly = true)
	public RoomDto requireRoom(String roomUuid) {
		return roomMapper.findByUuid(roomUuid)
				.orElseThrow(() -> new NotFoundException("방을 찾을 수 없습니다: " + roomUuid));
	}

	private String generateRoomUuid() {
		return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
	}

	private RoomResponse toResponse(RoomDto room) {
		List<ParticipantDto> participants = participantMapper.findActiveByRoomId(room.getRoomId());
		List<ParticipantResponse> participantResponses = participants.stream()
				.map(p -> toParticipantResponse(room, p))
				.toList();
		String hostParticipantId = participants.stream()
				.filter(ParticipantDto::isHost)
				.findFirst()
				.map(p -> String.valueOf(p.getParticipantId()))
				.orElse(null);
		RestaurantDto resolvedRestaurant = "RESOLVED".equals(room.getStage())
				? roomResultService.findResolvedRestaurant(room.getRoomId()).orElse(null)
				: null;
		boolean needsRelocation = Boolean.TRUE.equals(redisTemplate.hasKey(RELOCATION_KEY_PREFIX + room.getRoomId()));
		return new RoomResponse(
				room.getRoomUuid(), room.getMaxParticipants(), hostParticipantId, room.getMode(), room.getStage(),
				participantResponses, room.getMidpointLat(), room.getMidpointLng(), resolvedRestaurant, needsRelocation
		);
	}

	private ParticipantResponse toParticipantResponse(RoomDto room, ParticipantDto p) {
		RestaurantDto chosenRestaurant = selectionMapper.findChosenRestaurantByParticipantId(p.getParticipantId()).orElse(null);

		Integer walkTimeMinutes = null;
		List<RoutePointDto> walkRoute = List.of();
		String transitSummary = null;
		Integer transitTimeMinutes = null;
		Integer transitWalkToStationMinutes = null;
		Integer transitWalkFromStationMinutes = null;
		List<RoutePointDto> transitWalkToStationRoute = List.of();
		List<TransitLegDto> transitCoreLegs = List.of();
		List<RoutePointDto> transitWalkFromStationRoute = List.of();

		if ("RESOLVED".equals(room.getStage())) {
			List<ParticipantRouteDto> legs = participantRouteMapper.findLegsByParticipantId(p.getParticipantId());

			for (ParticipantRouteDto leg : legs) {
				List<RoutePointDto> points = participantRouteMapper.findPointsByRouteId(leg.getRouteId());
				if ("walk".equals(leg.getMode())) {
					walkTimeMinutes = leg.getTimeMinutes();
					walkRoute = points;
				} else if ("WALK_TO_STATION".equals(leg.getLegType())) {
					transitWalkToStationMinutes = leg.getTimeMinutes();
					transitWalkToStationRoute = points;
				} else if ("WALK_FROM_STATION".equals(leg.getLegType())) {
					transitWalkFromStationMinutes = leg.getTimeMinutes();
					transitWalkFromStationRoute = points;
				} else {
					// 대중교통 핵심 구간(WALKING/BUS/SUBWAY) - 카카오는 구간별 개별
					// 소요시간을 안 주고 전체 합계만 주기 때문에, 첫 구간에 전체를 몰아서
					// 저장해뒀다(RouteFiller 참고) - 여기서 그 값을 총 소요시간으로 쓴다.
					if (transitTimeMinutes == null || leg.getTimeMinutes() > 0) {
						transitTimeMinutes = leg.getTimeMinutes();
					}
					List<String> vehicles = leg.getVehicles() == null || leg.getVehicles().isBlank()
							? List.of()
							: List.of(leg.getVehicles().split(","));
					transitCoreLegs = new ArrayList<>(transitCoreLegs);
					transitCoreLegs.add(new TransitLegDto(leg.getLegType(), points, leg.getGuidance(), vehicles));
				}
			}

			if (!transitCoreLegs.isEmpty()) {
				transitSummary = transitCoreLegs.stream()
						.filter(leg -> !"WALKING".equals(leg.getType()) && leg.getGuidance() != null && !leg.getGuidance().isBlank())
						.map(TransitLegDto::getGuidance)
						.reduce((a, b) -> a + " → " + b)
						.orElse("");
			}
		}

		return new ParticipantResponse(
				String.valueOf(p.getParticipantId()), p.getNickname(), p.isHost(), p.isReady(), p.getPrefLat(), p.getPrefLng(),
				chosenRestaurant, walkTimeMinutes, walkRoute, transitSummary, transitTimeMinutes,
				transitWalkToStationMinutes, transitWalkFromStationMinutes, transitWalkToStationRoute,
				transitCoreLegs, transitWalkFromStationRoute
		);
	}
}
