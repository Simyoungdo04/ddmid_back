package com.ddmid.back.dto;

import java.util.List;

public record MultiMidpointOptionDto(
		MultiMidpointStationDto station,
		List<RestaurantDto> restaurants
) {
}
