package com.ddmid.back.dto;

public record MidpointCandidatesDto(
		MidpointStationDto walk,
		MidpointStationDto transit
) {
}
