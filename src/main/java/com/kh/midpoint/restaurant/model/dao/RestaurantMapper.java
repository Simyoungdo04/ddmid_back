package com.kh.midpoint.restaurant.model.dao;

import com.kh.midpoint.restaurant.model.dto.RestaurantRowDto;
import com.kh.midpoint.restaurant.model.vo.Restaurant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface RestaurantMapper {

	// RESTAURANT_ID 시퀀스 다음 값 - 불변 VO를 빌더로 만들기 전에 미리 받아온다.
	Long nextRestaurantId();

	// 같은 방에서 같은 카카오 장소를 이미 후보로 등록했으면 그 행을 그대로 재사용한다
	// (같은 검색 결과가 다시 보여질 때마다 중복 insert 하지 않기 위함).
	Optional<RestaurantRowDto> findByRoomIdAndKakaoPlaceId(Long roomId, Long kakaoPlaceId);

	void insert(Restaurant restaurant);

	void deleteByRoomId(@Param("roomId") Long roomId);
}
