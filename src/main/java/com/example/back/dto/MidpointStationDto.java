package com.example.back.dto;

public record MidpointStationDto(
		String name,
		double lat,
		double lng,
		int timeFromAMinutes,
		int timeFromBMinutes,
		int totalTimeMinutes
) {
}
