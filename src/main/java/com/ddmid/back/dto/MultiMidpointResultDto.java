package com.ddmid.back.dto;

public record MultiMidpointResultDto(
		MultiMidpointOptionDto walk,
		MultiMidpointOptionDto transit
) {
}
