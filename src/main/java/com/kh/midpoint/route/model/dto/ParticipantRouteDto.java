package com.kh.midpoint.route.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

// PARTICIPANT_ROUTE 테이블 조회 결과 한 행 - 참여자 경로의 구간 하나.
@Getter
@Setter
@NoArgsConstructor
@ToString
public class ParticipantRouteDto {
	private Long routeId;
	private Long participantId;
	private Long roomId;
	private String mode; // 'walk' | 'transit'
	private int legOrder;
	private String legType; // 도보: 'WALK', 대중교통: 카카오 원본 타입('WALKING'/'BUS'/'SUBWAY')
	private int timeMinutes;
	private String guidance;
	private String vehicles; // 콤마로 구분한 문자열
}
