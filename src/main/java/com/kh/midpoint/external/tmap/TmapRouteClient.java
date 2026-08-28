package com.kh.midpoint.external.tmap;

import com.kh.midpoint.common.exception.ExternalApiException;
import com.kh.midpoint.common.exception.NotFoundException;
import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tmap 보행자 경로안내 API 클라이언트. 두 지점 사이의 실제로 걸을 수 있는 경로(좌표 목록)와
 * 소요시간을 받아온다. 카카오/ODsay와 달리 좌표 평균 같은 근사치가 아니라 실제 도로/인도를
 * 따라가는 경로라, 두 지점 사이에 강·산이 있어 걸어서 갈 수 없는 경우 여기서 걸러진다.
 */
@Component
public class TmapRouteClient {

	private static final String START_NAME = "출발";
	private static final String END_NAME = "도착";
	// Tmap이 "waypoints are too near"로 거부하는 기준이 100m라, 그보다 조금 여유를 둔다.
	private static final double TOO_NEAR_METERS = 110.0;
	private static final double EARTH_RADIUS_METERS = 6371000;

	private final RestClient restClient;
	private final String appKey;

	public TmapRouteClient(@Value("${tmap.app-key}") String appKey) {
		this.appKey = appKey;
		this.restClient = RestClient.builder().build();
	}

	@Cacheable(cacheNames = "route-pedestrian", key = "#startX + ',' + #startY + ',' + #endX + ',' + #endY")
	public TmapRouteDto getPedestrianRoute(double startX, double startY, double endX, double endY) {
		// 참여자가 후보역 바로 근처(100m 이내)를 찍으면 Tmap이 아예 요청을 거부한다
		// (에러 코드 1007, "waypoints are too near"). 그 에러 응답이 가끔 gzip으로 와서
		// 문자열로 원인을 구분하는 게 신뢰할 수 없었다 — 그래서 애초에 Tmap을 부르기 전에
		// 거리부터 직접 계산해서, 이미 다 온 것과 같은 경우는 0분으로 바로 처리한다.
		if (distanceMeters(startY, startX, endY, endX) < TOO_NEAR_METERS) {
			return new TmapRouteDto(0, List.of(new RoutePointDto(startY, startX)));
		}

		JsonNode response;
		try {
			response = restClient.post()
					.uri(uriBuilder -> uriBuilder
							.scheme("https")
							.host("apis.openapi.sk.com")
							.path("/tmap/routes/pedestrian")
							.queryParam("version", "1")
							.build())
					.header("appKey", appKey)
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of(
							"startX", String.valueOf(startX),
							"startY", String.valueOf(startY),
							"endX", String.valueOf(endX),
							"endY", String.valueOf(endY),
							"startName", START_NAME,
							"endName", END_NAME,
							"reqCoordType", "WGS84GEO",
							"resCoordType", "WGS84GEO"
					))
					.retrieve()
					.body(JsonNode.class);
		} catch (RestClientResponseException e) {
			// 위 거리 체크로 "너무 가까움" 케이스는 이미 걸러졌으므로, 여기 도달하는 4xx/5xx는
			// 키/쿼터/네트워크 등 다른 문제일 가능성이 높다.
			throw new ExternalApiException("Tmap 요청 실패(status=" + e.getStatusCode().value() + ")");
		} catch (RestClientException e) {
			// 타임아웃/연결 끊김 등 상태코드 없는 네트워크 오류. 이걸 그대로 던지면 이 참여자
			// 한 명 때문에 resolve() 전체가 500으로 죽어서 방이 RESOLVING에 멈춰버린다 -
			// ExternalApiException(502)으로 감싸서 이 구간만 실패로 처리하게 한다.
			throw new ExternalApiException("Tmap 요청 실패: " + e.getMessage());
		}

		return parseRoute(response);
	}

	private double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lng2 - lng1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(dLng / 2) * Math.sin(dLng / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return EARTH_RADIUS_METERS * c;
	}

	private TmapRouteDto parseRoute(JsonNode response) {
		if (response == null) {
			throw new ExternalApiException("Tmap 응답을 받지 못했습니다.");
		}
		JsonNode features = response.path("features");
		if (!features.isArray() || features.isEmpty()) {
			throw new NotFoundException("두 지점을 잇는 도보 경로를 찾지 못했습니다.");
		}

		int totalTimeSeconds = 0;
		List<RoutePointDto> points = new ArrayList<>();

		for (JsonNode feature : features) {
			JsonNode properties = feature.path("properties");
			JsonNode geometry = feature.path("geometry");

			// 전체 소요시간(totalTime)은 여러 feature에 반복해서 들어있는데, 그중 가장 큰 값이
			// 실제 전체 값이다(중간 feature 중 일부는 0이거나 구간 값만 있을 수 있음).
			if (properties.has("totalTime")) {
				totalTimeSeconds = Math.max(totalTimeSeconds, properties.path("totalTime").asInt());
			}

			if ("LineString".equals(geometry.path("type").asText(""))) {
				for (JsonNode coord : geometry.path("coordinates")) {
					// Tmap 좌표는 GeoJSON 표준에 따라 [경도, 위도] 순서로 온다.
					double lng = coord.get(0).asDouble();
					double lat = coord.get(1).asDouble();
					points.add(new RoutePointDto(lat, lng));
				}
			}
		}

		if (points.isEmpty()) {
			throw new NotFoundException("도보 경로 좌표를 받지 못했습니다.");
		}

		int timeMinutes = (int) Math.ceil(totalTimeSeconds / 60.0);
		return new TmapRouteDto(timeMinutes, points);
	}
}
