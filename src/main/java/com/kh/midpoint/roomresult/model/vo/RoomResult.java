package com.kh.midpoint.roomresult.model.vo;

import lombok.Builder;
import lombok.Value;

// ROOM_RESULT 테이블 INSERT 전용 파라미터 - 방 하나가 최종적으로 어느 식당으로, 누구의
// 선택(게임 결과)으로 확정됐는지 기록한다. PK는 (RESULT_ID, ROOM_ID, RESTAURANT_ID, GAME_PARTICIPANT_ID) 복합키.
@Value
@Builder
public class RoomResult {
	Long resultId;
	Long roomId;
	Long restaurantId;
	Long gameParticipantId;
}
