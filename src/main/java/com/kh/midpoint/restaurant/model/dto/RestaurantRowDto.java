package com.kh.midpoint.restaurant.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

// RESTAURANT 테이블 조회 결과 한 행. 같은 방에서 같은 카카오 장소를 이미 후보로 등록했는지
// 확인할 때만 쓴다 - 검색 결과/화면 표시용 모양은 RestaurantDto를 쓴다.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RestaurantRowDto {
	private Long restaurantId;
	private Long roomId;
	private Long kakaoPlaceId;
	private String name;
	private String category;
	private String address;
	private String roadAddress;
	private String phone;
	private String placeUrl;
	private Double lat;
	private Double lng;
	private String source;
	private String addedBy;
	private LocalDateTime createdAt;
}
