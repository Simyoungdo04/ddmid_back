package com.kh.midpoint.restaurant.model.service;

import com.kh.midpoint.common.exception.NotFoundException;
import com.kh.midpoint.external.kakao.KakaoLocalClient;
import com.kh.midpoint.restaurant.model.dao.RestaurantMapper;
import com.kh.midpoint.restaurant.model.dto.RestaurantDto;
import com.kh.midpoint.restaurant.model.dto.RestaurantRowDto;
import com.kh.midpoint.restaurant.model.vo.Restaurant;
import com.kh.midpoint.room.model.dao.RoomMapper;
import com.kh.midpoint.room.model.dto.RoomDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RestaurantService {

	private final KakaoLocalClient kakaoLocalClient;
	private final RestaurantMapper restaurantMapper;
	private final RoomMapper roomMapper;

	public RestaurantService(KakaoLocalClient kakaoLocalClient, RestaurantMapper restaurantMapper, RoomMapper roomMapper) {
		this.kakaoLocalClient = kakaoLocalClient;
		this.restaurantMapper = restaurantMapper;
		this.roomMapper = roomMapper;
	}

	// roomId는 URL에 쓰이는 방 UUID(RoomDto.roomUuid)다 - 내부 시퀀스 PK(RoomDto.roomId)는
	// 외부에 노출하지 않고, 여기서만 조회해서 RESTAURANT 테이블 저장에 쓴다.
	@Transactional
	public List<RestaurantDto> findNearbyRestaurants(String roomUuid, double x, double y) {
		List<RestaurantDto> restaurants = kakaoLocalClient.findNearbyRestaurants(x, y);
		Long roomId = resolveRoomId(roomUuid);
		restaurants.forEach(dto -> ensurePersisted(roomId, dto));
		return restaurants;
	}

	@Transactional
	public List<RestaurantDto> searchByName(String roomUuid, String query, double x, double y) {
		List<RestaurantDto> restaurants = kakaoLocalClient.searchByName(query, x, y);
		Long roomId = resolveRoomId(roomUuid);
		restaurants.forEach(dto -> ensurePersisted(roomId, dto));
		return restaurants;
	}

	@Transactional(readOnly = true)
	public Long resolveRoomId(String roomUuid) {
		RoomDto room = roomMapper.findByUuid(roomUuid)
				.orElseThrow(() -> new NotFoundException("방을 찾을 수 없습니다: " + roomUuid));
		return room.getRoomId();
	}

	// 방 맥락이 없는 순수 장소 검색(예: 출발지 이름 검색) - 후보로 DB에 남기지 않는다.
	@Transactional(readOnly = true)
	public List<RestaurantDto> searchPlaces(String query) {
		return kakaoLocalClient.searchPlaces(query);
	}

	// 같은 방에서 같은 카카오 장소가 이미 후보로 저장돼 있으면 그 행의 ID를 그대로 쓰고,
	// 없으면 새로 저장한다. 검색 결과를 보여줄 때뿐 아니라, 참여자가 식당을 선택할 때도
	// (SelectionService) 그 식당이 RESTAURANT 테이블에 있어야 SELECTION이 참조할 수 있어서
	// 여기서 공용으로 쓴다.
	@Transactional
	public Long ensurePersisted(Long roomId, RestaurantDto dto) {
		long kakaoPlaceId = Long.parseLong(dto.getId());
		return restaurantMapper.findByRoomIdAndKakaoPlaceId(roomId, kakaoPlaceId)
				.map(RestaurantRowDto::getRestaurantId)
				.orElseGet(() -> {
					Long restaurantId = restaurantMapper.nextRestaurantId();
					Restaurant restaurant = Restaurant.builder()
							.restaurantId(restaurantId)
							.roomId(roomId)
							.kakaoPlaceId(kakaoPlaceId)
							.name(dto.getName())
							.category(dto.getCategory())
							.address(dto.getAddress())
							.roadAddress(dto.getRoadAddress())
							.phone(dto.getPhone())
							.placeUrl(dto.getPlaceUrl())
							.lat(dto.getLat())
							.lng(dto.getLng())
							.source("API 직접")
							.createdAt(LocalDateTime.now())
							.build();
					restaurantMapper.insert(restaurant);
					return restaurantId;
				});
	}
}
