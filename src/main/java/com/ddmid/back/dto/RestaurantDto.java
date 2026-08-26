package com.ddmid.back.dto;

public record RestaurantDto(
		String id,
		String name,
		String category,
		String address,
		String roadAddress,
		String phone,
		String placeUrl,
		Double lat,
		Double lng,
		int distanceMeters
) {
}
