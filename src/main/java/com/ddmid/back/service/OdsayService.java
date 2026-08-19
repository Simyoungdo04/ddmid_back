package com.ddmid.back.service;

import com.ddmid.back.dto.MidpointCandidatesDto;
import com.ddmid.back.dto.MidpointStationDto;
import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class OdsayService {

	private static final String PATH_SEARCH_URL = "https://api.odsay.com/v1/api/searchPubTransPathT";
	private static final int SEARCH_SUBWAY_AND_BUS = 0;
	private static final int TRAFFIC_TYPE_SUBWAY = 1;
	private static final int TRAFFIC_TYPE_BUS = 2;
	private static final String NO_TRANSIT_STOP_NAME = "대중교통 중간지점(경유역 없음)";
	private static final String WALK_OPTION_NAME = "도보 중간지점";
	private static final double WALK_SPEED_METERS_PER_MINUTE = 67.0; // 시속 4km 기준
	private static final double STRAIGHT_LINE_TO_ROAD_FACTOR = 1.3; // 직선거리를 실제 도보 경로 거리로 보정
	// ODsay 에러코드: 3/4/5=출발·도착지 정류장 없음, 6=서비스 지역 아님, -99=검색결과 없음
	private static final Set<String> NO_ROUTE_ERROR_CODES = Set.of("3", "4", "5", "6", "-99");

	private final RestClient restClient;
	private final String apiKey;

	public OdsayService(@Value("${odsay.api-key}") String apiKey) {
		this.apiKey = apiKey;
		this.restClient = RestClient.builder().build();
	}

	private record Candidate(String name, double lat, double lng, double timeFromStart) {
	}

	/**
	 * 두 지점 간 대중교통 최단(추천) 경로의 총 소요시간(분)만 필요할 때 쓰는 간단한 조회.
	 * N명 중간지점 후보를 평가할 때처럼 역 상세정보 없이 시간만 필요한 경우에 사용한다.
	 */
	public int getTravelTimeMinutes(double sx, double sy, double ex, double ey) {
		JsonNode response = queryPath(sx, sy, ex, ey);
		JsonNode paths = response.path("result").path("path");
		if (!paths.isArray() || paths.isEmpty()) {
			throw new IllegalStateException("두 지점을 잇는 대중교통 경로를 찾지 못했습니다.");
		}
		return paths.get(0).path("info").path("totalTime").asInt();
	}

	private JsonNode queryPath(double sx, double sy, double ex, double ey) {
		JsonNode response = restClient.get()
				.uri(uriBuilder -> uriBuilder
						.scheme("https")
						.host("api.odsay.com")
						.path("/v1/api/searchPubTransPathT")
						.queryParam("apiKey", apiKey)
						.queryParam("SX", sx)
						.queryParam("SY", sy)
						.queryParam("EX", ex)
						.queryParam("EY", ey)
						.queryParam("SearchPathType", SEARCH_SUBWAY_AND_BUS)
						.build())
				.retrieve()
				.body(JsonNode.class);

		if (response == null) {
			throw new IllegalStateException("ODsay 응답을 받지 못했습니다.");
		}
		if (response.has("error")) {
			JsonNode errorNode = response.path("error");
			JsonNode firstError = errorNode.isArray() ? errorNode.path(0) : errorNode;
			String code = firstError.path("code").asText("");

			// 3/4/5/6=출발·도착지 정류장 없음, -99=검색결과 없음 -> 대중교통으로 이어지지 않는
			// 지점이라는 뜻이라, 바다처럼 사람이 이동할 수 없는 구간일 가능성이 높다.
			if (NO_ROUTE_ERROR_CODES.contains(code)) {
				throw new IllegalStateException("사람이 이동할 수 없는 구간(바다 등)이 포함되어 있어 경로를 탐색할 수 없습니다.");
			}

			// ODsay는 에러 형태에 따라 메시지 필드명이 message/msg로 다르게 온다.
			String message = firstError.path("message").asText(firstError.path("msg").asText("경로를 찾을 수 없습니다."));
			throw new IllegalStateException(message);
		}
		return response;
	}

	/**
	 * A, B 두 지점에 대해 "도보 기준"과 "대중교통 기준" 중간지점 후보를 각각 계산해서 반환한다.
	 * 어느 쪽이 더 나은지는 거리/시간으로 자동 판단하지 않고, 둘 다 계산해서 프론트에서
	 * 사용자가 고를 수 있게 한다.
	 *
	 * - 도보: A, B 좌표의 기하학적 중간지점. 직선거리 기준으로 예상 소요시간을 추정한다.
	 * - 대중교통: ODsay 추천 경로상의 역/정류장 중 A로부터의 소요시간이 전체의 절반에
	 *   가장 가까운 곳. 경유 정류장이 하나도 없을 만큼 A,B가 인접한 경우(예: 바로 옆 역)에는
	 *   좌표 중간지점을 대신 사용한다.
	 */
	public MidpointCandidatesDto findMidpointOptions(double ax, double ay, double bx, double by) {
		JsonNode response = queryPath(ax, ay, bx, by);

		JsonNode paths = response.path("result").path("path");
		if (!paths.isArray() || paths.isEmpty()) {
			// 대중교통 경로 자체가 없다는 건 바다처럼 사람이 이동할 수 없는 구간일 가능성이 높다.
			throw new IllegalStateException("사람이 이동할 수 없는 구간(바다 등)이 포함되어 있어 경로를 탐색할 수 없습니다.");
		}

		double midLat = (ay + by) / 2.0;
		double midLng = (ax + bx) / 2.0;
		double pointDistance = response.path("result").path("pointDistance").asDouble();
		int walkTotalTime = (int) Math.ceil(pointDistance * STRAIGHT_LINE_TO_ROAD_FACTOR / WALK_SPEED_METERS_PER_MINUTE);
		int walkTimeFromA = walkTotalTime / 2;
		MidpointStationDto walkOption = new MidpointStationDto(
				WALK_OPTION_NAME, midLat, midLng, walkTimeFromA, Math.max(walkTotalTime - walkTimeFromA, 0), walkTotalTime
		);

		JsonNode bestPath = paths.get(0);
		int totalTime = bestPath.path("info").path("totalTime").asInt();

		List<Candidate> candidates = new ArrayList<>();
		int elapsed = 0;
		for (JsonNode subPath : bestPath.path("subPath")) {
			int sectionTime = subPath.path("sectionTime").asInt();

			int trafficType = subPath.path("trafficType").asInt();
			if (trafficType == TRAFFIC_TYPE_SUBWAY || trafficType == TRAFFIC_TYPE_BUS) {
				JsonNode stations = subPath.path("passStopList").path("stations");
				int stationCount = stations.size();

				if (stationCount > 0) {
					for (JsonNode station : stations) {
						// 버스가 실제로 정차하지 않는 통과 정류장은 하차 후보에서 제외한다.
						if ("Y".equals(station.path("isNonStop").asText(null))) {
							continue;
						}

						// ODsay 응답의 index는 문서상 1부터라고 되어 있지만 실제로는 0부터 온다 (0=출발역).
						int index = station.path("index").asInt(0);
						double fraction = stationCount > 1 ? (double) index / (stationCount - 1) : 0.0;
						double timeFromStart = elapsed + sectionTime * fraction;

						candidates.add(new Candidate(
								station.path("stationName").asText(),
								station.path("y").asDouble(),
								station.path("x").asDouble(),
								timeFromStart
						));
					}
				}
			}

			elapsed += sectionTime;
		}

		// 맨 처음/마지막은 각각 A, B 자신이 타고 내리는 역이라 "중간"이 아니므로 후보에서 제외한다.
		List<Candidate> interior = candidates.size() > 2
				? candidates.subList(1, candidates.size() - 1)
				: List.of();

		MidpointStationDto transitOption;
		if (interior.isEmpty()) {
			int timeFromA = totalTime / 2;
			transitOption = new MidpointStationDto(
					NO_TRANSIT_STOP_NAME, midLat, midLng, timeFromA, Math.max(totalTime - timeFromA, 0), totalTime
			);
		} else {
			double targetTime = totalTime / 2.0;
			Candidate best = interior.get(0);
			double bestDiff = Math.abs(best.timeFromStart() - targetTime);
			for (Candidate candidate : interior) {
				double diff = Math.abs(candidate.timeFromStart() - targetTime);
				if (diff < bestDiff) {
					bestDiff = diff;
					best = candidate;
				}
			}

			int timeFromA = (int) Math.round(best.timeFromStart());
			transitOption = new MidpointStationDto(
					best.name(), best.lat(), best.lng(), timeFromA, Math.max(totalTime - timeFromA, 0), totalTime
			);
		}

		return new MidpointCandidatesDto(walkOption, transitOption);
	}
}
