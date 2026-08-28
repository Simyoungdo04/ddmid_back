package com.kh.midpoint.roomresult.model.dao;

import com.kh.midpoint.restaurant.model.dto.RestaurantDto;
import com.kh.midpoint.roomresult.model.vo.RoomResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface RoomResultMapper {

	// RESULT_ID 시퀀스 다음 값 - 불변 VO를 빌더로 만들기 전에 미리 받아온다.
	Long nextResultId();

	void insert(RoomResult roomResult);

	// 확정된 식당의 상세 정보 - ROOM_RESULT와 RESTAURANT를 조인해서 가져온다.
	Optional<RestaurantDto> findResolvedRestaurantByRoomId(@Param("roomId") Long roomId);

	void deleteByRoomId(@Param("roomId") Long roomId);
}
