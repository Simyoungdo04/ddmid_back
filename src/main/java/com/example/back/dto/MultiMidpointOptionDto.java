package com.example.back.dto;

import java.util.List;

public record MultiMidpointOptionDto(
		MultiMidpointStationDto station,
		List<RestaurantDto> restaurants
) {
}
