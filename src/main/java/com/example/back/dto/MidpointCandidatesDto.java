package com.example.back.dto;

public record MidpointCandidatesDto(
		MidpointStationDto walk,
		MidpointStationDto transit
) {
}
