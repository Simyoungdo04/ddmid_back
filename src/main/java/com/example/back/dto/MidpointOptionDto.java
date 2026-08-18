package com.example.back.dto;

import java.util.List;

public record MidpointOptionDto(
		MidpointStationDto station,
		List<RestaurantDto> restaurants
) {
}
