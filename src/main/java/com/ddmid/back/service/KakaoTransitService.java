package com.ddmid.back.service;

import com.ddmid.back.dto.RoutePointDto;
import com.ddmid.back.dto.TransitRouteDto;
import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 카카오맵 대중교통 경로 조회 API 클라이언트. 카카오 로컬 API와 같은 REST 키
 * (`kakao.rest-api-key`)를 그대로 쓰고, 같은 카카오맵 무료 쿼터에 포함된다.
 * ODsay를 대신해서 두 지점 사이의 대중교통 경로(시간 + 좌표)를 받아온다.
 */
@Service
public class KakaoTransitService {

	private final RestClient restClient;
	private final String apiKey;

	public KakaoTransitService(@Value("${kakao.rest-api-key}") String apiKey) {
		this.apiKey = apiKey;
		this.restClient = RestClient.builder().build();
	}

	public TransitRouteDto getRoute(double sx, double sy, double ex, double ey) {
		JsonNode response = restClient.get()
				.uri(uriBuilder -> uriBuilder
						.scheme("https")
						.host("dapi.kakao.com")
						.path("/v2/routing/publictraffic")
						.queryParam("start_x", sx)
						.queryParam("start_y", sy)
						.queryParam("end_x", ex)
						.queryParam("end_y", ey)
						.build())
				.header("Authorization", "KakaoAK " + apiKey)
				.retrieve()
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					// 인증 실패, 쿼터 초과, 파라미터 오류 등 카카오 쪽 문제. "경로 없음"과는 다르다.
					throw new IllegalStateException("카카오 대중교통 경로 조회 실패(status=" + res.getStatusCode().value() + ")");
				})
				.body(JsonNode.class);

		return parseRoute(response, sx, sy);
	}

	private TransitRouteDto parseRoute(JsonNode response, double sx, double sy) {
		if (response == null) {
			throw new IllegalStateException("카카오 대중교통 경로 응답을 받지 못했습니다.");
		}

		// 정상 응답이어도 경로를 못 찾으면 status가 "NO_RESULTS"(경로 없음),
		// "EQUAL_POINTS"(출발/도착지 동일) 등으로 오고 routes가 빈 배열이다.
		String status = response.path("status").asText("");

		// 참여자가 후보역 바로 그 자리(또는 매우 가까운 곳)를 찍은 경우 실제로 자주 발생한다.
		// "경로 없음"이 아니라 "이미 다 왔다"는 뜻이므로, 이 후보를 버리지 않고 0분으로 처리한다.
		if ("EQUAL_POINTS".equals(status)) {
			return new TransitRouteDto(0, List.of(new RoutePointDto(sy, sx)), "이미 도착");
		}

		JsonNode routes = response.path("routes");
		if (!"OK".equals(status) || !routes.isArray() || routes.isEmpty()) {
			throw new NoRouteFoundException("두 지점을 잇는 대중교통 경로를 찾지 못했습니다.");
		}

		// routes 배열이 소요시간순으로 정렬되어 온다는 보장이 없어서, 직접 최솟값을 고른다.
		JsonNode bestRoute = null;
		int bestTimeSeconds = Integer.MAX_VALUE;
		for (JsonNode route : routes) {
			int totalTime = route.path("properties").path("totalTime").asInt();
			if (totalTime < bestTimeSeconds) {
				bestTimeSeconds = totalTime;
				bestRoute = route;
			}
		}

		List<RoutePointDto> points = new ArrayList<>();
		List<String> legDescriptions = new ArrayList<>();
		for (JsonNode step : bestRoute.path("steps")) {
			for (JsonNode coord : step.path("path").path("points")) {
				// 카카오 좌표도 GeoJSON 표준과 같은 [경도, 위도] 순서로 온다.
				double lng = coord.get(0).asDouble();
				double lat = coord.get(1).asDouble();
				points.add(new RoutePointDto(lat, lng));
			}

			// guidance는 "1호선 (종로3가 > 동대문)"처럼 이미 사람이 읽을 수 있는 형태로 온다.
			// 도보 환승 구간(WALKING)은 "~까지 도보로 이동" 같은 문구라 역 이름이 아니므로 뺀다.
			String type = step.path("properties").path("type").asText("");
			String guidance = step.path("properties").path("guidance").asText("");
			if (!"WALKING".equals(type) && !guidance.isBlank()) {
				legDescriptions.add(guidance);
			}
		}

		if (points.isEmpty()) {
			throw new NoRouteFoundException("대중교통 경로 좌표를 받지 못했습니다.");
		}

		int timeMinutes = (int) Math.ceil(bestTimeSeconds / 60.0);
		String summary = legDescriptions.isEmpty() ? "" : String.join(" → ", legDescriptions);
		return new TransitRouteDto(timeMinutes, points, summary);
	}
}
