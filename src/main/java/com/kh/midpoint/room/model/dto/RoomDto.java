package com.kh.midpoint.room.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

// ROOM 테이블 한 행 조회 결과. TRAVEL_MODE/STAGE는 ERD에 없어서 이번에 추가한 컬럼
// (docs/2026-08-26_작업자_DB-스키마-추가검토.md 참고) - 방 흐름 전체를 이 두 값으로 굴린다.
@Getter
@Setter
@NoArgsConstructor
@ToString
public class RoomDto {
	private Long roomId;
	private String roomUuid;
	private int maxParticipants;
	private Double midpointLat;
	private Double midpointLng;
	private String midpointSource; // 'STATION' | 'CENTER'
	private String mode; // 'WALK' | 'TRANSIT'
	private String stage; // WAITING -> MODE_SELECTED -> MIDPOINT_FOUND -> RESOLVING -> RESOLVED
	private LocalDateTime createdAt;
	private LocalDateTime expiresAt;
}
