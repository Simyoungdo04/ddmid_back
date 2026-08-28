package com.kh.midpoint.selection.model.service;

import com.kh.midpoint.restaurant.model.dto.RestaurantDto;
import com.kh.midpoint.restaurant.model.service.RestaurantService;
import com.kh.midpoint.selection.model.dao.SelectionMapper;
import com.kh.midpoint.selection.model.vo.Selection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SelectionService {

	private final SelectionMapper selectionMapper;
	private final RestaurantService restaurantService;

	public SelectionService(SelectionMapper selectionMapper, RestaurantService restaurantService) {
		this.selectionMapper = selectionMapper;
		this.restaurantService = restaurantService;
	}

	// 참여자가 식당을 고르면(또는 다시 고르면) 이전 선택을 지우고 새로 기록한다. 프론트가
	// 보내주는 건 카카오 검색 결과 모양(RestaurantDto)이라, RESTAURANT 테이블에 먼저
	// 있는지 확인하고 없으면 저장한 뒤 그 ID로 SELECTION을 남긴다.
	@Transactional
	public void choose(Long roomId, Long participantId, RestaurantDto restaurant) {
		Long restaurantId = restaurantService.ensurePersisted(roomId, restaurant);
		selectionMapper.deleteByParticipantId(participantId);
		Long selectionId = selectionMapper.nextSelectionId();
		Selection selection = Selection.builder()
				.selectionId(selectionId)
				.participantId(participantId)
				.restaurantId(restaurantId)
				.build();
		selectionMapper.insert(selection);
	}
}
