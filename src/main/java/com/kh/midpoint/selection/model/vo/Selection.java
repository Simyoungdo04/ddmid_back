package com.kh.midpoint.selection.model.vo;

import lombok.Builder;
import lombok.Value;

// SELECTION 테이블 INSERT 전용 파라미터 - 참여자 한 명이 식당 하나를 골랐다는 기록.
@Value
@Builder
public class Selection {
	Long selectionId;
	Long participantId;
	Long restaurantId;
}
