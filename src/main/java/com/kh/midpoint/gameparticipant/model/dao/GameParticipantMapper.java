package com.kh.midpoint.gameparticipant.model.dao;

import com.kh.midpoint.gameparticipant.model.vo.GameParticipant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GameParticipantMapper {

	// GAME_PARTICIPANT_ID 시퀀스 다음 값 - 불변 VO를 빌더로 만들기 전에 미리 받아온다.
	Long nextGameParticipantId();

	void insert(GameParticipant gameParticipant);

	void deleteByRoomId(@Param("roomId") Long roomId);
}
