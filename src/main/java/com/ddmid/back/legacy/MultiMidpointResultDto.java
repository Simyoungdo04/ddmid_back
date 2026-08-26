package com.ddmid.back.legacy;

public record MultiMidpointResultDto(
		MultiMidpointOptionDto walk,
		MultiMidpointOptionDto transit
) {
}
