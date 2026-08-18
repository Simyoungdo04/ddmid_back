package com.example.back.dto;

public record MultiMidpointResultDto(
		MultiMidpointOptionDto walk,
		MultiMidpointOptionDto transit
) {
}
