package com.ddmid.back.controller;

import com.ddmid.back.dto.RestaurantDto;
import com.ddmid.back.service.KakaoRestaurantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RestaurantController {

	private final KakaoRestaurantService kakaoRestaurantService;

	public RestaurantController(KakaoRestaurantService kakaoRestaurantService) {
		this.kakaoRestaurantService = kakaoRestaurantService;
	}

	@GetMapping("/api/restaurants")
	public List<RestaurantDto> nearbyRestaurants(@RequestParam double x, @RequestParam double y) {
		return kakaoRestaurantService.findNearbyRestaurants(x, y);
	}

	@GetMapping("/api/restaurants/search")
	public List<RestaurantDto> searchRestaurantsByName(
			@RequestParam String query, @RequestParam double x, @RequestParam double y
	) {
		return kakaoRestaurantService.searchByName(query, x, y);
	}

	@GetMapping("/api/places/search")
	public List<RestaurantDto> searchPlaces(@RequestParam String query) {
		return kakaoRestaurantService.searchPlaces(query);
	}
}
