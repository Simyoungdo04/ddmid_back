package com.ddmid.back.service;

import com.ddmid.back.dto.RestaurantDto;
import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

// 식당/장소 검색만 담당한다. 지하철역 검색은 KakaoStationService가 따로 맡는다 - 같은
// 카카오 로컬 API를 쓰지만 목적(식당 추천 vs 중간지점 후보)이 달라서 분리했다.
@Service
public class KakaoRestaurantService {

	private static final String CATEGORY_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/category.json";
	private static final String KEYWORD_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";
	private static final String FOOD_CATEGORY_GROUP_CODE = "FD6";
	private static final int NAME_SEARCH_RADIUS_METERS = 1500;
	// 중간지점 주변에 식당이 없으면 반경을 500m씩 넓혀가며 재시도하고, 3km까지도 없으면 포기한다.
	private static final int NEARBY_SEARCH_INITIAL_RADIUS_METERS = 1000;
	private static final int NEARBY_SEARCH_RADIUS_STEP_METERS = 500;
	private static final int NEARBY_SEARCH_MAX_RADIUS_METERS = 3000;
	private static final int CATEGORY_RESULT_SIZE = 5;
	private static final int KEYWORD_RESULT_SIZE = 3;

	private final RestClient categoryClient;
	private final RestClient keywordClient;

	public KakaoRestaurantService(@Value("${kakao.rest-api-key}") String kakaoRestApiKey) {
		this.categoryClient = RestClient.builder()
				.baseUrl(CATEGORY_SEARCH_URL)
				.defaultHeader("Authorization", "KakaoAK " + kakaoRestApiKey)
				.build();
		this.keywordClient = RestClient.builder()
				.baseUrl(KEYWORD_SEARCH_URL)
				.defaultHeader("Authorization", "KakaoAK " + kakaoRestApiKey)
				.build();
	}

	/**
	 * 중간지점 주변 식당을 찾는다. 처음엔 1km 반경으로 찾고, 결과가 없으면 500m씩 반경을
	 * 넓혀가며 재시도한다. 3km까지 넓혀도 없으면 빈 리스트를 반환한다(=주변에 식당 없음).
	 */
	public List<RestaurantDto> findNearbyRestaurants(double x, double y) {
		for (int radius = NEARBY_SEARCH_INITIAL_RADIUS_METERS;
				radius <= NEARBY_SEARCH_MAX_RADIUS_METERS;
				radius += NEARBY_SEARCH_RADIUS_STEP_METERS) {
			List<RestaurantDto> restaurants = findNearbyRestaurants(x, y, radius);
			if (!restaurants.isEmpty()) {
				return restaurants;
			}
		}
		return new ArrayList<>();
	}

	private List<RestaurantDto> findNearbyRestaurants(double x, double y, int radiusMeters) {
		JsonNode response = categoryClient.get()
				.uri(uriBuilder -> uriBuilder
						.queryParam("category_group_code", FOOD_CATEGORY_GROUP_CODE)
						.queryParam("x", x)
						.queryParam("y", y)
						.queryParam("radius", radiusMeters)
						.queryParam("sort", "accuracy")
						.queryParam("size", CATEGORY_RESULT_SIZE)
						.build())
				.retrieve()
				.body(JsonNode.class);

		return toRestaurantList(response);
	}

	/**
	 * 특정 좌표(중간지점 등) 주변 반경 1km 이내에서 이름으로 식당을 검색한다.
	 * 반경 밖이거나 일치하는 곳이 없으면 빈 리스트를 반환한다.
	 */
	public List<RestaurantDto> searchByName(String query, double x, double y) {
		JsonNode response = keywordClient.get()
				.uri(uriBuilder -> uriBuilder
						.queryParam("query", query)
						.queryParam("x", x)
						.queryParam("y", y)
						.queryParam("radius", NAME_SEARCH_RADIUS_METERS)
						.queryParam("sort", "accuracy")
						.queryParam("size", KEYWORD_RESULT_SIZE)
						.build())
				.retrieve()
				.body(JsonNode.class);

		return toRestaurantList(response);
	}

	/**
	 * 기준 좌표 없이 이름만으로 장소를 검색한다. 방 만들기/입장 시 "출발지 검색"처럼 아직
	 * 중심이 될 좌표가 없는 상황에 쓴다.
	 */
	public List<RestaurantDto> searchPlaces(String query) {
		JsonNode response = keywordClient.get()
				.uri(uriBuilder -> uriBuilder
						.queryParam("query", query)
						.queryParam("sort", "accuracy")
						.queryParam("size", KEYWORD_RESULT_SIZE)
						.build())
				.retrieve()
				.body(JsonNode.class);

		return toRestaurantList(response);
	}

	private List<RestaurantDto> toRestaurantList(JsonNode response) {
		List<RestaurantDto> restaurants = new ArrayList<>();
		if (response == null) {
			return restaurants;
		}

		for (JsonNode doc : response.path("documents")) {
			restaurants.add(new RestaurantDto(
					doc.path("id").asText(),
					doc.path("place_name").asText(),
					doc.path("category_name").asText(),
					doc.path("address_name").asText(),
					doc.path("road_address_name").asText(),
					doc.path("phone").asText(),
					doc.path("place_url").asText(),
					doc.path("y").asDouble(),
					doc.path("x").asDouble(),
					doc.path("distance").asInt(0)
			));
		}
		return restaurants;
	}
}
