package com.ddmid.back.legacy;

import com.ddmid.back.dto.RestaurantDto;

import java.util.List;

public record MultiMidpointOptionDto(
		MultiMidpointStationDto station,
		List<RestaurantDto> restaurants
) {
}
