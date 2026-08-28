package com.kh.midpoint.room.controller;

import com.kh.midpoint.common.response.ApiResponse;
import com.kh.midpoint.participant.model.dto.JoinRoomRequest;
import com.kh.midpoint.participant.model.dto.UpdateLocationRequest;
import com.kh.midpoint.participant.model.service.ParticipantService;
import com.kh.midpoint.restaurant.model.dto.RestaurantDto;
import com.kh.midpoint.room.model.dto.RoomCreateRequest;
import com.kh.midpoint.room.model.dto.RoomResponse;
import com.kh.midpoint.room.model.dto.SetModeRequest;
import com.kh.midpoint.room.model.service.RoomDeletionService;
import com.kh.midpoint.room.model.service.RoomService;
import com.kh.midpoint.selection.model.service.SelectionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

	private final RoomService roomService;
	private final ParticipantService participantService;
	private final SelectionService selectionService;
	private final RoomDeletionService roomDeletionService;

	public RoomController(
			RoomService roomService, ParticipantService participantService, SelectionService selectionService,
			RoomDeletionService roomDeletionService
	) {
		this.roomService = roomService;
		this.participantService = participantService;
		this.selectionService = selectionService;
		this.roomDeletionService = roomDeletionService;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<RoomResponse>> create(@RequestBody RoomCreateRequest request) {
		RoomResponse room = roomService.createRoom(request.getHeadCount());
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created("방이 생성되었습니다.", room));
	}

	@GetMapping("/{roomId}")
	public ApiResponse<RoomResponse> get(@PathVariable String roomId) {
		return ApiResponse.ok(roomService.getRoom(roomId));
	}

	@PostMapping("/{roomId}/participants")
	public ResponseEntity<ApiResponse<Map<String, String>>> join(@PathVariable String roomId, @RequestBody JoinRoomRequest request) {
		Long participantId = participantService.join(roomId, request.getNickname(), request.getLat(), request.getLng());
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.created("참여자가 등록되었습니다.", Map.of("id", String.valueOf(participantId))));
	}

	@DeleteMapping("/{roomId}/participants/{participantId}")
	public ResponseEntity<Void> leave(@PathVariable String roomId, @PathVariable Long participantId) {
		participantService.leave(roomId, participantId);
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/{roomId}/participants/{participantId}/location")
	public ResponseEntity<ApiResponse<Void>> updateLocation(
			@PathVariable String roomId, @PathVariable Long participantId, @RequestBody UpdateLocationRequest request
	) {
		participantService.updateLocation(roomId, participantId, request.getLat(), request.getLng());
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.updated());
	}

	@PatchMapping("/{roomId}/mode")
	public ResponseEntity<ApiResponse<Void>> setMode(@PathVariable String roomId, @RequestBody SetModeRequest request) {
		roomService.setMode(roomId, request.getMode());
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.updated());
	}

	@PostMapping("/{roomId}/midpoint")
	public ResponseEntity<ApiResponse<Void>> findMidpoint(@PathVariable String roomId) {
		roomService.findMidpoint(roomId);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.updated());
	}

	@PostMapping("/{roomId}/relocation-request")
	public ResponseEntity<ApiResponse<Void>> requestRelocation(@PathVariable String roomId) {
		roomService.requestRelocation(roomId);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.updated());
	}

	@PostMapping("/{roomId}/participants/{participantId}/restaurant")
	public ResponseEntity<ApiResponse<Void>> chooseRestaurant(
			@PathVariable String roomId, @PathVariable Long participantId, @RequestBody RestaurantDto restaurant
	) {
		Long numericRoomId = roomService.requireRoom(roomId).getRoomId();
		selectionService.choose(numericRoomId, participantId, restaurant);
		roomService.markResolving(roomId);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.updated());
	}

	@PostMapping("/{roomId}/resolve")
	public ResponseEntity<ApiResponse<Void>> resolve(@PathVariable String roomId) {
		roomService.resolve(roomId);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.updated());
	}

	// 테스트 단계에서 방을 바로 지우기 위한 수동 삭제 - 자동 만료(RoomCleanupScheduler)를
	// 3시간씩 기다릴 필요 없이 프론트 "방 삭제" 버튼에서 바로 호출한다.
	@DeleteMapping("/{roomId}")
	public ResponseEntity<Void> delete(@PathVariable String roomId) {
		roomDeletionService.deleteRoom(roomService.requireRoom(roomId).getRoomId());
		return ResponseEntity.noContent().build();
	}
}
