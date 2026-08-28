package com.kh.midpoint.participant.model.dao;

import com.kh.midpoint.participant.model.dto.ParticipantDto;
import com.kh.midpoint.participant.model.vo.Participant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface ParticipantMapper {

	// PARTICIPANT_ID 시퀀스 다음 값 - 불변 VO를 빌더로 만들기 전에 미리 받아온다.
	Long nextParticipantId();

	void insert(Participant participant);

	// 나간 사람(LEFT_AT NOT NULL)은 제외한, 지금 방에 있는 참여자 전원 - 입장 순서대로.
	List<ParticipantDto> findActiveByRoomId(@Param("roomId") Long roomId);

	// 닉네임 중복 검사용 - 나간 사람은 검사 대상에서 뺀다(나간 닉네임은 재사용 가능).
	Optional<ParticipantDto> findActiveByRoomIdAndNickname(@Param("roomId") Long roomId, @Param("nickname") String nickname);

	Optional<ParticipantDto> findByRoomIdAndParticipantId(
			@Param("roomId") Long roomId, @Param("participantId") Long participantId
	);

	void markLeft(@Param("roomId") Long roomId, @Param("participantId") Long participantId, @Param("leftAt") LocalDateTime leftAt);

	void updateIsHost(@Param("roomId") Long roomId, @Param("participantId") Long participantId, @Param("isHost") String isHost);

	void updateLocation(
			@Param("roomId") Long roomId, @Param("participantId") Long participantId,
			@Param("lat") Double lat, @Param("lng") Double lng
	);

	void deleteByRoomId(@Param("roomId") Long roomId);
}
