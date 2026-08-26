package com.ddmid.back.service;

import com.ddmid.back.dto.RestaurantDto;
import com.ddmid.back.dto.RoutePointDto;
import com.ddmid.back.dto.TransitLegDto;

import java.util.List;

// 방 안 참여자 상태. 메모리에서만 관리되는 임시 상태라 record가 아니라 그냥 값이 바뀌는
// 클래스로 둔다 (Jackson이 public 필드를 그대로 JSON으로 내려준다).
public class Participant {
	public final String id;
	public String nickname;
	public Double lat;
	public Double lng;
	public RestaurantDto chosenRestaurant;

	// 도보(직선 목적지까지 그대로) - Tmap은 좌표를 그대로 쓰기 때문에 식당 좌표 정확도 문제가 없다.
	public Integer walkTimeMinutes;
	public List<RoutePointDto> walkRoute = List.of();

	// 대중교통 - 카카오 응답은 "타는 역"~"내리는 역"까지만 주기 때문에(19장 참고), 출발지->타는 역,
	// 내리는 역->식당 두 구간을 Tmap으로 채운다. 세 구간을 하나로 합치지 않고 따로 둬야
	// 지도에서 "도보 -> 대중교통 -> 도보"가 구간별로 다른 스타일(점선/실선)로 보인다.
	public String transitSummary;
	public Integer transitTimeMinutes;
	public Integer transitWalkToStationMinutes;
	public Integer transitWalkFromStationMinutes;
	public List<RoutePointDto> transitWalkToStationRoute = List.of();
	// 버스/지하철 구간을 지도에서 다른 색으로 그리기 위해 구간(도보 환승/버스/지하철)별로 나눠서 둔다.
	public List<TransitLegDto> transitCoreLegs = List.of();
	public List<RoutePointDto> transitWalkFromStationRoute = List.of();

	public Participant(String id, String nickname, Double lat, Double lng) {
		this.id = id;
		this.nickname = nickname;
		this.lat = lat;
		this.lng = lng;
	}
}
