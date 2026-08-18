package com.example.back.dto;

import java.util.List;

public record MultiMidpointStationDto(
		String name,
		double lat,
		double lng,
		List<Integer> timesFromEachMinutes,
		int maxTimeMinutes
) {
}
