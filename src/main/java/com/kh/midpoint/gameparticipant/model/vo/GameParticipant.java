package com.kh.midpoint.gameparticipant.model.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

// GAME_PARTICIPANT 테이블 INSERT 전용 파라미터. PK는 (GAME_PARTICIPANT_ID, PARTICIPANT_ID, ROOM_ID) 복합키.
@Value
@Builder
public class GameParticipant {
	Long gameParticipantId;
	Long participantId;
	Long roomId;
	String gameSessionId;
	String isWinner; // 'Y' / 'N'
	LocalDateTime joinedAt;
}
