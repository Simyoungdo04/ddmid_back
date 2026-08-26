package com.ddmid.back.service;

import com.ddmid.back.dto.NearbyStationDto;
import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

// 지하철역 검색만 담당한다. 식당 검색(KakaoRestaurantService)과 같은 카카오 로컬 카테고리
// 검색 API를 쓰지만, 목적이 다르다 - 이쪽은 식당 추천용이 아니라 MidpointFinder가 중간지점
// 후보를 고를 때만 쓴다.
@Service
public class KakaoStationService {

	private static final String CATEGORY_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/category.json";
	private static final String SUBWAY_CATEGORY_GROUP_CODE = "SW8";
	private static final int SUBWAY_SEARCH_RADIUS_METERS = 10000;

	private final RestClient categoryClient;

	public KakaoStationService(@Value("${kakao.rest-api-key}") String kakaoRestApiKey) {
		this.categoryClient = RestClient.builder()
				.baseUrl(CATEGORY_SEARCH_URL)
				.defaultHeader("Authorization", "KakaoAK " + kakaoRestApiKey)
				.build();
	}

	// 특정 좌표(예: N명 좌표의 중심점) 근처 지하철역을 가까운 순으로 최대 count개 찾는다.
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
}
