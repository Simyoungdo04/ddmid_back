package com.kh.midpoint.restaurant.model.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

// RESTAURANT 테이블 INSERT 전용 파라미터. 불변 - 값을 다 갖춘 뒤 빌더로 한 번에 만든다.
// PK는 (RESTAURANT_ID, ROOM_ID) 복합키 — 같은 카카오 장소라도 방마다 별도 행으로 저장한다.
@Value
@Builder
public class Restaurant {
	Long restaurantId;
	Long roomId;
	Long kakaoPlaceId;
	String name;
	String category;
	String address;
	String roadAddress;
	String phone;
	String placeUrl;
	Double lat;
	Double lng;
	String source; // 'API 직접' 등 — ERD 기본값
	String addedBy;
	LocalDateTime createdAt;
}
