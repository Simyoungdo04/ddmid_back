package com.kh.midpoint.restaurant.controller;

import com.kh.midpoint.common.response.ApiResponse;
import com.kh.midpoint.restaurant.model.dto.RestaurantDto;
import com.kh.midpoint.restaurant.model.service.RestaurantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RestaurantController {

	private final RestaurantService restaurantService;

	public RestaurantController(RestaurantService restaurantService) {
		this.restaurantService = restaurantService;
	}

	@GetMapping("/api/restaurants")
	public ApiResponse<List<RestaurantDto>> nearbyRestaurants(
			@RequestParam String roomId, @RequestParam double x, @RequestParam double y
	) {
		return ApiResponse.ok(restaurantService.findNearbyRestaurants(roomId, x, y));
	}

	@GetMapping("/api/restaurants/search")
	public ApiResponse<List<RestaurantDto>> searchRestaurantsByName(
			@RequestParam String roomId, @RequestParam String query, @RequestParam double x, @RequestParam double y
	) {
		return ApiResponse.ok(restaurantService.searchByName(roomId, query, x, y));
	}

	@GetMapping("/api/places/search")
	public ApiResponse<List<RestaurantDto>> searchPlaces(@RequestParam String query) {
		return ApiResponse.ok(restaurantService.searchPlaces(query));
	}
}
