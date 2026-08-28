package com.kh.midpoint.restaurant.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

// 카카오 검색 결과 하나 / 화면에 보여줄 식당 후보 하나의 모양. 요청(식당 선택)과 응답
// (검색 결과, 확정된 식당) 양쪽에 다 쓰인다.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RestaurantDto {
	private String id;
	private String name;
	private String category;
	private String address;
	private String roadAddress;
	private String phone;
	private String placeUrl;
	private Double lat;
	private Double lng;
	private int distanceMeters;
}
