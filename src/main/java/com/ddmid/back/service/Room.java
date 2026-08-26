package com.ddmid.back.service;

import com.ddmid.back.dto.RestaurantDto;

import java.util.ArrayList;
import java.util.List;

// WAITING -> MODE_SELECTED -> MIDPOINT_FOUND -> RESOLVING -> RESOLVED
public class Room {
	public final String id;
	public final int capacity;
	public String hostParticipantId;
	public String mode;
	public String stage = "WAITING";
	public final List<Participant> participants = new ArrayList<>();
	public Double midpointLat;
	public Double midpointLng;
	public RestaurantDto resolvedRestaurant;

	public Room(String id, int capacity) {
		this.id = id;
		this.capacity = capacity;
	}
}
