package com.kh.midpoint.route.model.vo;

import lombok.Builder;
import lombok.Value;

// PARTICIPANT_ROUTE 테이블 INSERT 전용 파라미터 - 참여자 경로의 구간 하나. (아직 팀 확정 전
// 제안 스키마 - docs/2026-08-26_작업자_DB-스키마-추가검토.md 6번 참고)
@Value
@Builder
public class ParticipantRoute {
	Long routeId;
	Long participantId;
	Long roomId;
	String mode; // 'walk' | 'transit'
	int legOrder;
	String legType; // 도보: 'WALK', 대중교통: 카카오 원본 타입('WALKING'/'BUS'/'SUBWAY')
	int timeMinutes;
	String guidance;
	String vehicles; // 콤마로 구분한 문자열
}
