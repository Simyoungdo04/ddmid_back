package com.kh.midpoint.participant.model.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

// PARTICIPANT 테이블 INSERT 전용 파라미터. 불변 - 값을 다 갖춘 뒤 빌더로 한 번에 만든다.
// PK는 (PARTICIPANT_ID, ROOM_ID) 복합키.
@Value
@Builder
public class Participant {
	Long participantId;
	Long roomId;
	String nickname;
	String isHost; // 'Y' / 'N'
	String isReady; // 'Y' / 'N'
	Double prefLat;
	Double prefLng;
	LocalDateTime joinedAt;
}
