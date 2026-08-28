package com.kh.midpoint.roomresult.model.service;

import com.kh.midpoint.restaurant.model.dto.RestaurantDto;
import com.kh.midpoint.roomresult.model.dao.RoomResultMapper;
import com.kh.midpoint.roomresult.model.vo.RoomResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class RoomResultService {

	private final RoomResultMapper roomResultMapper;

	public RoomResultService(RoomResultMapper roomResultMapper) {
		this.roomResultMapper = roomResultMapper;
	}

	@Transactional
	public void record(Long roomId, Long restaurantId, Long gameParticipantId) {
		Long resultId = roomResultMapper.nextResultId();
		RoomResult result = RoomResult.builder()
				.resultId(resultId)
				.roomId(roomId)
				.restaurantId(restaurantId)
				.gameParticipantId(gameParticipantId)
				.build();
		roomResultMapper.insert(result);
	}

	@Transactional(readOnly = true)
	public Optional<RestaurantDto> findResolvedRestaurant(Long roomId) {
		return roomResultMapper.findResolvedRestaurantByRoomId(roomId);
	}
}
