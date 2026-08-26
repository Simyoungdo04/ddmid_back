package com.ddmid.back.controller;

import com.ddmid.back.dto.JoinRoomRequest;
import com.ddmid.back.dto.RestaurantDto;
import com.ddmid.back.dto.SetModeRequest;
import com.ddmid.back.service.Participant;
import com.ddmid.back.service.Room;
import com.ddmid.back.service.RoomService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

	private final RoomService roomService;

	public RoomController(RoomService roomService) {
		this.roomService = roomService;
	}

	@PostMapping
	public Room create(@RequestParam int headCount) {
		return roomService.createRoom(headCount);
	}

	@GetMapping("/{roomId}")
	public Room get(@PathVariable String roomId) {
		return roomService.getRoom(roomId);
	}

	@PostMapping("/{roomId}/participants")
	public Participant join(@PathVariable String roomId, @RequestBody JoinRoomRequest request) {
		return roomService.join(roomId, request.nickname(), request.lat(), request.lng());
	}

	@DeleteMapping("/{roomId}/participants/{participantId}")
	public Room leave(@PathVariable String roomId, @PathVariable String participantId) {
		return roomService.leave(roomId, participantId);
	}

	@PatchMapping("/{roomId}/mode")
	public Room setMode(@PathVariable String roomId, @RequestBody SetModeRequest request) {
		return roomService.setMode(roomId, request.mode());
	}

	@PostMapping("/{roomId}/midpoint")
	public Room findMidpoint(@PathVariable String roomId) {
		return roomService.findMidpoint(roomId);
	}

	@PostMapping("/{roomId}/participants/{participantId}/restaurant")
	public Room chooseRestaurant(
			@PathVariable String roomId, @PathVariable String participantId, @RequestBody RestaurantDto restaurant
	) {
		return roomService.chooseRestaurant(roomId, participantId, restaurant);
	}

	@PostMapping("/{roomId}/resolve")
	public Room resolve(@PathVariable String roomId) {
		return roomService.resolve(roomId);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<?> handleNotFound(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
	}
}
