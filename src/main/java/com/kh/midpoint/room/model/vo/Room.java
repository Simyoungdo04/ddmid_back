package com.kh.midpoint.room.model.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

// ROOM 테이블 INSERT 전용 파라미터. 불변 - 값을 다 갖춘 뒤 빌더로 한 번에 만든다.
@Value
@Builder
public class Room {
	Long roomId;
	String roomUuid;
	int maxParticipants;
	String stage; // WAITING -> MODE_SELECTED -> MIDPOINT_FOUND -> RESOLVING -> RESOLVED
	LocalDateTime createdAt;
	LocalDateTime expiresAt;
}
