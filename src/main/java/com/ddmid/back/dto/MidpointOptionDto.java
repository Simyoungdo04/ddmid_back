package com.ddmid.back.dto;

import java.util.List;

public record MidpointOptionDto(
		MidpointStationDto station,
		List<RestaurantDto> restaurants
) {
}
