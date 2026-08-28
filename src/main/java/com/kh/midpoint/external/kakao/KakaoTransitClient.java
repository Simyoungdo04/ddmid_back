package com.kh.midpoint.external.kakao;

import com.kh.midpoint.common.exception.ExternalApiException;
import com.kh.midpoint.common.exception.NotFoundException;
import com.kh.midpoint.external.tmap.RoutePointDto;
import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

/**
 * 카카오맵 대중교통 경로 조회 API 클라이언트. 카카오 로컬 API(KakaoLocalClient)와 같은
 * REST 키(`kakao.rest-api-key`)를 그대로 쓰고, 같은 카카오맵 무료 쿼터에 포함된다.
 * ODsay를 대신해서 두 지점 사이의 대중교통 경로(시간 + 좌표)를 받아온다.
 */
@Component
public class KakaoTransitClient {

	private final RestClient restClient;
	private final String apiKey;

	public KakaoTransitClient(@Value("${kakao.rest-api-key}") String apiKey) {
		this.apiKey = apiKey;
		this.restClient = RestClient.builder().build();
	}

	@Cacheable(cacheNames = "route-transit", key = "#sx + ',' + #sy + ',' + #ex + ',' + #ey")
	public TransitRouteDto getRoute(double sx, double sy, double ex, double ey) {
		JsonNode response;
		try {
			response = restClient.get()
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
						throw new ExternalApiException(
								"카카오 대중교통 경로 조회 실패(status=" + res.getStatusCode().value() + ")"
						);
					})
					.body(JsonNode.class);
		} catch (ExternalApiException e) {
			throw e;
		} catch (RestClientException e) {
			// 타임아웃/연결 끊김 등 상태코드 없는 네트워크 오류. 이 참여자 한 명 때문에
			// resolve() 전체가 500으로 죽어서 방이 RESOLVING에 멈추는 걸 막는다.
			throw new ExternalApiException("카카오 대중교통 경로 조회 실패: " + e.getMessage());
		}

		return parseRoute(response, sx, sy);
	}

	private TransitRouteDto parseRoute(JsonNode response, double sx, double sy) {
		if (response == null) {
			throw new ExternalApiException("카카오 대중교통 경로 응답을 받지 못했습니다.");
		}

		// 정상 응답이어도 경로를 못 찾으면 status가 "NO_RESULTS"(경로 없음),
		// "EQUAL_POINTS"(출발/도착지 동일) 등으로 오고 routes가 빈 배열이다.
		String status = response.path("status").asText("");

		// 참여자가 후보역 바로 그 자리(또는 매우 가까운 곳)를 찍은 경우 실제로 자주 발생한다.
		// "경로 없음"이 아니라 "이미 다 왔다"는 뜻이므로, 이 후보를 버리지 않고 0분으로 처리한다.
		if ("EQUAL_POINTS".equals(status)) {
			List<RoutePointDto> herePoint = List.of(new RoutePointDto(sy, sx));
			return new TransitRouteDto(0, herePoint, "이미 도착", List.of(new TransitLegDto("WALKING", herePoint, "이미 도착", List.of())));
		}

		JsonNode routes = response.path("routes");
		if (!"OK".equals(status) || !routes.isArray() || routes.isEmpty()) {
			throw new NotFoundException("두 지점을 잇는 대중교통 경로를 찾지 못했습니다.");
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
		List<TransitLegDto> legs = new ArrayList<>();
		for (JsonNode step : bestRoute.path("steps")) {
			List<RoutePointDto> legPoints = new ArrayList<>();
			for (JsonNode coord : step.path("path").path("points")) {
				// 카카오 좌표도 GeoJSON 표준과 같은 [경도, 위도] 순서로 온다.
				double lng = coord.get(0).asDouble();
				double lat = coord.get(1).asDouble();
				legPoints.add(new RoutePointDto(lat, lng));
			}
			points.addAll(legPoints);

			// guidance는 "1호선 (종로3가 > 동대문)"처럼 이미 사람이 읽을 수 있는 형태로 온다.
			// 도보 환승 구간(WALKING)은 "~까지 도보로 이동" 같은 문구라 역 이름이 아니므로 뺀다.
			String type = step.path("properties").path("type").asText("");
			String guidance = step.path("properties").path("guidance").asText("");
			if (!"WALKING".equals(type) && !guidance.isBlank()) {
				legDescriptions.add(guidance);
			}
			// properties.vehicles에 이 구간을 지나는 노선이 전부 개별로 들어있다 - guidance의
			// "외 N대" 요약과 달리 번호를 하나하나 다 보여줄 때 쓴다. 지하철은 같은 호선이
			// 중복으로 들어오는 경우가 있어 이름 기준으로 중복만 제거한다.
			List<String> vehicles = new ArrayList<>();
			for (JsonNode vehicle : step.path("properties").path("vehicles")) {
				String vehicleType = vehicle.path("type").asText("");
				String vehicleName = vehicle.path("name").asText("");
				String label = vehicleType.isBlank() ? vehicleName : vehicleType + " " + vehicleName;
				if (!label.isBlank() && !vehicles.contains(label)) {
					vehicles.add(label);
				}
			}

			// 버스/지하철 구간을 지도에서 다른 색으로 그리고, 클릭했을 때 상세보기로 안내
			// 문구를 보여줄 수 있도록 구간별로 남긴다.
			if (!legPoints.isEmpty()) {
				legs.add(new TransitLegDto(type, legPoints, guidance, vehicles));
			}
		}

		if (points.isEmpty()) {
			throw new NotFoundException("대중교통 경로 좌표를 받지 못했습니다.");
		}

		int timeMinutes = (int) Math.ceil(bestTimeSeconds / 60.0);
		String summary = legDescriptions.isEmpty() ? "" : String.join(" → ", legDescriptions);
		return new TransitRouteDto(timeMinutes, points, summary, legs);
	}
}
