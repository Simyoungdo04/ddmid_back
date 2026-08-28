package com.kh.midpoint.selection.model.dao;

import com.kh.midpoint.restaurant.model.dto.RestaurantDto;
import com.kh.midpoint.selection.model.vo.Selection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface SelectionMapper {

	// SELECTION_ID 시퀀스 다음 값 - 불변 VO를 빌더로 만들기 전에 미리 받아온다.
	Long nextSelectionId();

	void insert(Selection selection);

	void deleteByParticipantId(@Param("participantId") Long participantId);

	// SELECTION에는 ROOM_ID가 없어서(PARTICIPANT_ID, RESTAURANT_ID로만 구성) PARTICIPANT를
	// 거쳐서 지운다.
	void deleteByRoomId(@Param("roomId") Long roomId);

	// 참여자가 고른 식당의 상세 정보 - SELECTION과 RESTAURANT를 조인해서 가져온다.
	// 아직 안 골랐으면 비어있다.
	Optional<RestaurantDto> findChosenRestaurantByParticipantId(@Param("participantId") Long participantId);
}
