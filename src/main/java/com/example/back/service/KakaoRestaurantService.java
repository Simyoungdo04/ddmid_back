package com.example.back.service;

import com.example.back.dto.NearbyStationDto;
import com.example.back.dto.RestaurantDto;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Service
public class KakaoRestaurantService {

	private static final String CATEGORY_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/category.json";
	private static final String KEYWORD_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";
	private static final String FOOD_CATEGORY_GROUP_CODE = "FD6";
	private static final String SUBWAY_CATEGORY_GROUP_CODE = "SW8";
	private static final int SEARCH_RADIUS_METERS = 1000;
	private static final int SUBWAY_SEARCH_RADIUS_METERS = 10000;
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

	public List<RestaurantDto> findNearbyRestaurants(double x, double y) {
		JsonNode response = categoryClient.get()
				.uri(uriBuilder -> uriBuilder
						.queryParam("category_group_code", FOOD_CATEGORY_GROUP_CODE)
						.queryParam("x", x)
						.queryParam("y", y)
						.queryParam("radius", SEARCH_RADIUS_METERS)
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
						.queryParam("radius", SEARCH_RADIUS_METERS)
						.queryParam("sort", "accuracy")
						.queryParam("size", KEYWORD_RESULT_SIZE)
						.build())
				.retrieve()
				.body(JsonNode.class);

		return toRestaurantList(response);
	}

	/**
	 * 특정 좌표(예: N명 좌표의 중심점) 근처 지하철역을 가까운 순으로 최대 count개 찾는다.
	 */
	public List<NearbyStationDto> findNearbySubwayStations(double x, double y, int count) {
		JsonNode response = categoryClient.get()
				.uri(uriBuilder -> uriBuilder
						.queryParam("category_group_code", SUBWAY_CATEGORY_GROUP_CODE)
						.queryParam("x", x)
						.queryParam("y", y)
						.queryParam("radius", SUBWAY_SEARCH_RADIUS_METERS)
						.queryParam("sort", "distance")
						.queryParam("size", count)
						.build())
				.retrieve()
				.body(JsonNode.class);

		List<NearbyStationDto> stations = new ArrayList<>();
		if (response == null) {
			return stations;
		}
		for (JsonNode doc : response.path("documents")) {
			stations.add(new NearbyStationDto(
					doc.path("place_name").asText(),
					doc.path("y").asDouble(),
					doc.path("x").asDouble()
			));
		}
		return stations;
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
