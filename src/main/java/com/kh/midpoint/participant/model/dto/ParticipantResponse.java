package com.kh.midpoint.participant.model.dto;

import com.kh.midpoint.external.kakao.TransitLegDto;
import com.kh.midpoint.external.tmap.RoutePointDto;
import com.kh.midpoint.restaurant.model.dto.RestaurantDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

// 방 화면에 필요한 참여자 정보 전부(기본 정보 + 선택한 식당 + 계산된 경로)를 한 번에 담는다.
// PARTICIPANT/SELECTION/PARTICIPANT_ROUTE 여러 테이블을 조합해서 만든다.
// id는 내부 PK(Long)를 문자열로 바꾼 값이다 - 프론트가 localStorage에 문자열로 저장해두고
// 비교(===)하기 때문에 타입을 맞춰야 한다.
// isHost/isReady는 Boolean(박싱 타입)으로 둔다 - boolean 기본형이었으면 Lombok이
// isHost() 게터를 만들어도 Jackson이 JSON 키를 "host"로 벗겨내서 프론트가 읽는
// "isHost" 필드명이 깨진다. Boolean으로 두면 게터가 getIsHost()가 되어 JSON 키가
// "isHost" 그대로 유지된다.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ParticipantResponse {
	private String id;
	private String nickname;
	private Boolean isHost;
	private Boolean isReady;
	private Double lat;
	private Double lng;
	private RestaurantDto chosenRestaurant;
	private Integer walkTimeMinutes;
	private List<RoutePointDto> walkRoute;
	private String transitSummary;
	private Integer transitTimeMinutes;
	private Integer transitWalkToStationMinutes;
	private Integer transitWalkFromStationMinutes;
	private List<RoutePointDto> transitWalkToStationRoute;
	private List<TransitLegDto> transitCoreLegs;
	private List<RoutePointDto> transitWalkFromStationRoute;
}
