package com.ddmid.back.controller;

import com.ddmid.back.dto.MidpointCandidatesDto;
import com.ddmid.back.dto.MidpointOptionDto;
import com.ddmid.back.dto.MidpointResultDto;
import com.ddmid.back.dto.MultiMidpointResultDto;
import com.ddmid.back.dto.PointRequest;
import com.ddmid.back.dto.RestaurantDto;
import com.ddmid.back.service.KakaoRestaurantService;
import com.ddmid.back.service.MultiMidpointService;
import com.ddmid.back.service.OdsayService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class MidpointController {

	private final OdsayService odsayService;
	private final KakaoRestaurantService kakaoRestaurantService;
	private final MultiMidpointService multiMidpointService;

	public MidpointController(
			OdsayService odsayService,
			KakaoRestaurantService kakaoRestaurantService,
			MultiMidpointService multiMidpointService
	) {
		this.odsayService = odsayService;
		this.kakaoRestaurantService = kakaoRestaurantService;
		this.multiMidpointService = multiMidpointService;
	}

	@GetMapping("/api/midpoint")
	public ResponseEntity<?> midpoint(
			@RequestParam double ax, @RequestParam double ay,
			@RequestParam double bx, @RequestParam double by
	) {
		try {
			MidpointCandidatesDto candidates = odsayService.findMidpointOptions(ax, ay, bx, by);

			List<RestaurantDto> walkRestaurants = kakaoRestaurantService.findNearbyRestaurants(
					candidates.walk().lng(), candidates.walk().lat());
			List<RestaurantDto> transitRestaurants = kakaoRestaurantService.findNearbyRestaurants(
					candidates.transit().lng(), candidates.transit().lat());

			return ResponseEntity.ok(new MidpointResultDto(
					new MidpointOptionDto(candidates.walk(), walkRestaurants),
					new MidpointOptionDto(candidates.transit(), transitRestaurants)
			));
		} catch (IllegalStateException e) {
			return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
		}
	}

	@PostMapping("/api/midpoint/multi")
	public ResponseEntity<?> multiMidpoint(@RequestBody List<PointRequest> points) {
		try {
			MultiMidpointResultDto result = multiMidpointService.findMidpoint(points);
			return ResponseEntity.ok(result);
		} catch (IllegalStateException | IllegalArgumentException e) {
			return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
		}
	}
}
