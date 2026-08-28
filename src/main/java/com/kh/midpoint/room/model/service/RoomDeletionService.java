package com.kh.midpoint.room.model.service;

import com.kh.midpoint.gameparticipant.model.dao.GameParticipantMapper;
import com.kh.midpoint.participant.model.dao.ParticipantMapper;
import com.kh.midpoint.restaurant.model.dao.RestaurantMapper;
import com.kh.midpoint.room.model.dao.RoomMapper;
import com.kh.midpoint.roomresult.model.dao.RoomResultMapper;
import com.kh.midpoint.route.model.dao.ParticipantRouteMapper;
import com.kh.midpoint.selection.model.dao.SelectionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 방 하나와 그 밑에 딸린 데이터를 전부 지운다. SELECTION/GAME_PARTICIPANT/ROOM_RESULT/
// PARTICIPANT_ROUTE는 ROOM에 DB FK 제약이 없어서(로직으로만 연결) 여기서
// 순서를 지켜가며 직접 지워야 한다 - PARTICIPANT/RESTAURANT는 FK가 있어서 방보다 먼저
// 지워야 하고, PARTICIPANT_ROUTE_POINT는 PARTICIPANT_ROUTE의 ON DELETE CASCADE로 같이 지워진다.
@Service
public class RoomDeletionService {

	private final RoomMapper roomMapper;
	private final ParticipantMapper participantMapper;
	private final ParticipantRouteMapper participantRouteMapper;
	private final RestaurantMapper restaurantMapper;
	private final SelectionMapper selectionMapper;
	private final GameParticipantMapper gameParticipantMapper;
	private final RoomResultMapper roomResultMapper;

	public RoomDeletionService(
			RoomMapper roomMapper, ParticipantMapper participantMapper, ParticipantRouteMapper participantRouteMapper,
			RestaurantMapper restaurantMapper, SelectionMapper selectionMapper,
			GameParticipantMapper gameParticipantMapper, RoomResultMapper roomResultMapper
	) {
		this.roomMapper = roomMapper;
		this.participantMapper = participantMapper;
		this.participantRouteMapper = participantRouteMapper;
		this.restaurantMapper = restaurantMapper;
		this.selectionMapper = selectionMapper;
		this.gameParticipantMapper = gameParticipantMapper;
		this.roomResultMapper = roomResultMapper;
	}

	@Transactional
	public void deleteRoom(Long roomId) {
		participantRouteMapper.deleteByRoomId(roomId);
		roomResultMapper.deleteByRoomId(roomId);
		gameParticipantMapper.deleteByRoomId(roomId);
		selectionMapper.deleteByRoomId(roomId);
		restaurantMapper.deleteByRoomId(roomId);
		participantMapper.deleteByRoomId(roomId);
		roomMapper.deleteById(roomId);
	}
}
