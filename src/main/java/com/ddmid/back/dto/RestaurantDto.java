package com.ddmid.back.dto;

import java.io.Serializable;

// Redis 캐시에 JDK 직렬화로 저장하려면 Serializable이어야 한다.
public record RestaurantDto(
		String id,
		String name,
		String category,
		String address,
		String roadAddress,
		String phone,
		String placeUrl,
		double lat,
		double lng,
		int distanceMeters
) implements Serializable {
}
