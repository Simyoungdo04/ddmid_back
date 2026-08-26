package com.ddmid.back.service;

import com.ddmid.back.dto.NearbyStationDto;
import com.ddmid.back.dto.RestaurantDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 방(Room) 책임만 담당한다 - 참여자 목록/단계 관리, 방장 위임, 식당 확정 흐름의 "순서"만
// 다루고, 실제 중간지점 계산이나 경로 조회 같은 지도 관련 일은 MidpointFinder/RouteFiller에게,
// 만장일치가 아닐 때 승자를 정하는 일은 RestaurantTiebreaker에게 맡긴다. 방 상태는 메모리에만
// 두는 간단한 구현이라 서버 재시작하면 방이 다 사라지고, 여러 서버 인스턴스로는 확장 안
// 된다 - 지금 범위(테스트용)에서는 충분하다.
@Service
public class RoomService {

	private final Map<String, Room> rooms = new ConcurrentHashMap<>();
	private final MidpointFinder midpointFinder;
	private final RouteFiller routeFiller;
	private final RestaurantTiebreaker restaurantTiebreaker;

	public RoomService(MidpointFinder midpointFinder, RouteFiller routeFiller, RestaurantTiebreaker restaurantTiebreaker) {
		this.midpointFinder = midpointFinder;
		this.routeFiller = routeFiller;
		this.restaurantTiebreaker = restaurantTiebreaker;
	}

	public Room createRoom(int capacity) {
		Room room = new Room(generateRoomId(), capacity);
		rooms.put(room.id, room);
		return room;
	}

	public Room getRoom(String roomId) {
		return require(roomId);
	}

	public Participant join(String roomId, String nickname, Double lat, Double lng) {
		Room room = require(roomId);
		// 방 하나에 참여자가 동시에(더블클릭 등으로) 들어오는 경우, 닉네임 중복 검사와
		// 리스트 추가가 원자적이지 않으면 같은 닉네임이 두 번 들어갈 수 있다 - room 단위로 잠근다.
		synchronized (room) {
			boolean nicknameTaken = room.participants.stream()
					.anyMatch(p -> p.nickname.equalsIgnoreCase(nickname));
			if (nicknameTaken) {
				throw new IllegalArgumentException("이미 사용 중인 닉네임입니다: " + nickname);
			}
			Participant participant = new Participant(UUID.randomUUID().toString().substring(0, 8), nickname, lat, lng);
			room.participants.add(participant);
			if (room.hostParticipantId == null) {
				room.hostParticipantId = participant.id;
			}
			return participant;
		}
	}

	// 방장이 나가면 남은 사람 중 아무나(목록의 첫 번째)를 새 방장으로 넘긴다 - 방장이 없으면
	// 모드 선택/중간지점 찾기 버튼을 아무도 못 누르게 되기 때문이다.
	public Room leave(String roomId, String participantId) {
		Room room = require(roomId);
		synchronized (room) {
			boolean removed = room.participants.removeIf(p -> p.id.equals(participantId));
			if (!removed) {
				throw new IllegalArgumentException("참여자를 찾을 수 없습니다: " + participantId);
			}
			if (participantId.equals(room.hostParticipantId)) {
				room.hostParticipantId = room.participants.isEmpty() ? null : room.participants.get(0).id;
			}
		}
		return room;
	}

	public Room setMode(String roomId, String mode) {
		Room room = require(roomId);
		room.mode = mode;
		room.stage = "MODE_SELECTED";
		return room;
	}

	// 방장이 미리 고른 이동수단(도보/대중교통) 기준으로 중간지점을 찾는다. 실제 계산은
	// MidpointFinder(지도 책임)에게 맡기고, 여기서는 결과를 방 상태에 반영만 한다.
	public Room findMidpoint(String roomId) {
		Room room = require(roomId);
		if (room.mode == null) {
			throw new IllegalArgumentException("이동 방법을 먼저 선택해주세요.");
		}

		NearbyStationDto midpoint = midpointFinder.find(room.participants, room.mode);
		room.midpointLat = midpoint.lat();
		room.midpointLng = midpoint.lng();
		room.stage = "MIDPOINT_FOUND";
		return room;
	}

	public Room chooseRestaurant(String roomId, String participantId, RestaurantDto restaurant) {
		Room room = require(roomId);
		requireParticipant(room, participantId).chosenRestaurant = restaurant;
		room.stage = "RESOLVING";
		return room;
	}

	// 참여자 전원이 식당을 골라야 확정할 수 있다. 전원 일치하면 그 식당, 하나라도 다르면
	// RestaurantTiebreaker가 한 명을 뽑아 그 사람이 고른 식당으로 확정한다. 이후 참여자별
	// 경로(도보/대중교통)는 RouteFiller(지도 책임)에게 맡겨서 채운다.
	public Room resolve(String roomId) {
		Room room = require(roomId);
		List<Participant> chosen = room.participants.stream().filter(p -> p.chosenRestaurant != null).toList();
		if (chosen.size() < room.participants.size()) {
			throw new IllegalArgumentException("아직 전원이 식당을 고르지 않았습니다.");
		}

		boolean unanimous = chosen.stream().map(p -> p.chosenRestaurant.id()).distinct().count() == 1;
		Participant winner = unanimous ? chosen.get(0) : restaurantTiebreaker.pickWinner(chosen);
		room.resolvedRestaurant = winner.chosenRestaurant;

		for (Participant participant : room.participants) {
			// 여기서 던지는 예외는 이미 RouteFiller 안에서 처리되지만, 혹시 놓친 케이스가
			// 있어도 참여자 한 명 때문에 방 전체가 RESOLVING에 멈추면 안 되므로 한 번 더 감싼다.
			try {
				routeFiller.fillWalkRoute(participant, room.resolvedRestaurant);
			} catch (RuntimeException e) {
				// 이 사람만 도보 경로를 못 찾은 것 -> 나머지 결과는 그대로 보여준다.
			}
			try {
				routeFiller.fillTransitRoute(participant, room.resolvedRestaurant);
			} catch (RuntimeException e) {
				// 이 사람만 대중교통 경로를 못 찾은 것 -> 나머지 결과는 그대로 보여준다.
			}
		}
		room.stage = "RESOLVED";
		return room;
	}

	private Participant requireParticipant(Room room, String participantId) {
		return room.participants.stream()
				.filter(p -> p.id.equals(participantId))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("참여자를 찾을 수 없습니다: " + participantId));
	}

	private Room require(String roomId) {
		Room room = rooms.get(roomId);
		if (room == null) {
			throw new IllegalArgumentException("방을 찾을 수 없습니다: " + roomId);
		}
		return room;
	}

	private String generateRoomId() {
		return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
	}
}
