package com.kh.midpoint.room.model.dto;

import com.kh.midpoint.participant.model.dto.ParticipantResponse;
import com.kh.midpoint.restaurant.model.dto.RestaurantDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

// 방 화면 하나를 그리는 데 필요한 모든 걸 담아서 내려주는 응답 모양. ROOM/PARTICIPANT/
// SELECTION/ROOM_RESULT/PARTICIPANT_ROUTE 여러 테이블을 조합해서 만든다
// (RoomService.toResponse() 참고). roomId는 URL에 쓰는 방 UUID다.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RoomResponse {
	private String roomId;
	private int capacity;
	private String hostParticipantId;
	private String mode;
	private String stage;
	private List<ParticipantResponse> participants;
	private Double midpointLat;
	private Double midpointLng;
	private RestaurantDto resolvedRestaurant;
	private boolean needsRelocation;
}
