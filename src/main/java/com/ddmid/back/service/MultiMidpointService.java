package com.ddmid.back.service;

import com.ddmid.back.dto.MultiMidpointOptionDto;
import com.ddmid.back.dto.MultiMidpointResultDto;
import com.ddmid.back.dto.MultiMidpointStationDto;
import com.ddmid.back.dto.NearbyStationDto;
import com.ddmid.back.dto.PointRequest;
import com.ddmid.back.dto.RestaurantDto;
import com.ddmid.back.dto.RoutePointDto;
import com.ddmid.back.dto.TmapRouteDto;
import com.ddmid.back.dto.TransitRouteDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 2명 이상(N명)의 중간지점을 찾는다.
 * 1) N개 좌표의 중심점 근처 지하철역 후보 몇 개를 뽑고
 * 2) 각 후보에 대해 N명 전원의 이동시간을 조회해서(도보는 Tmap, 대중교통은 카카오맵)
 * 3) 그중 "가장 오래 걸리는 사람의 시간"이 가장 작은 후보를 고른다.
 *
 * 도보와 대중교통은 후보를 각자 독립적으로 평가하므로 서로 다른 역이 선택될 수 있다.
 *
 * 원래 2명일 때는 ODsay 추천 경로 위에서 "시간상 정확히 절반인 역"을 바로 골라내는
 * 별도 알고리즘(OdsayService.findMidpointOptions, 현재는 삭제됨)을 썼었는데, 인원이
 * 늘어나면 그 방식 자체가 성립하지 않아서(ODsay는 두 지점 사이 경로만 조회 가능) 이
 * 중심점+후보역 방식으로 로직을 통일했다.
 *
 * 대중교통 옵션은 2026-08-20부터 ODsay 무료 등급 일일 호출 한도가 1,000건에서 30건으로
 * 축소되면서 ODsay를 뺐다가, 카카오맵 REST API에 대중교통 경로 조회 기능
 * (`KakaoTransitService`)이 있는 걸 확인하고 다시 붙였다. 자세한 경위는 맵 기능
 * 명세서 문서를 참고.
 */
@Service
public class MultiMidpointService {

	private static final int CANDIDATE_STATION_COUNT = 3;
	private static final String WALK_OPTION_NAME = "도보 중간지점(중심점)";
	private static final String NO_TRANSIT_CANDIDATE_NAME = "대중교통 경유지 없음(중심점)";
	private static final String UNREACHABLE_MESSAGE =
			"사람이 이동할 수 없는 구간(바다 등)이 포함되어 있어 경로를 탐색할 수 없습니다.";
	// 이보다 가까우면 대중교통 조회 없이도 당연히 이동 가능하다고 본다.
	private static final double CLEARLY_REACHABLE_METERS = 1000.0;
	private static final double WALK_SPEED_METERS_PER_MINUTE = 67.0; // 시속 4km 기준
	private static final double STRAIGHT_LINE_TO_ROAD_FACTOR = 1.3; // 직선거리를 실제 도보 경로 거리로 보정
	private static final double EARTH_RADIUS_METERS = 6371000;

	private final KakaoRestaurantService kakaoRestaurantService;
	private final TmapService tmapService;
	private final KakaoTransitService kakaoTransitService;

	public MultiMidpointService(
			KakaoRestaurantService kakaoRestaurantService, TmapService tmapService, KakaoTransitService kakaoTransitService
	) {
		this.kakaoRestaurantService = kakaoRestaurantService;
		this.tmapService = tmapService;
		this.kakaoTransitService = kakaoTransitService;
	}

	public MultiMidpointResultDto findMidpoint(List<PointRequest> points) {
		if (points.size() < 2) {
			throw new IllegalArgumentException("2명 이상일 때 쓰는 기능입니다.");
		}

		assertAllPointsReachable(points);

		double centroidLat = points.stream().mapToDouble(PointRequest::y).average().orElseThrow();
		double centroidLng = points.stream().mapToDouble(PointRequest::x).average().orElseThrow();

		List<NearbyStationDto> candidates =
				kakaoRestaurantService.findNearbySubwayStations(centroidLng, centroidLat, CANDIDATE_STATION_COUNT);

		// 도보는 후보역 3곳 외에 중심점(좌표 평균) 자체도 후보로 함께 경쟁시킨다. 참여자들이
		// 가까이 몰려있을 때는 역까지 가는 것보다 그냥 중심점 근처에서 만나는 게 더 공평한
		// 경우가 많은데, 후보역만 평가하면 이런 경우를 절대 고를 수 없었다. 대중교통은 역
		// 기준이 자연스러워서(중심점 자체는 대중교통으로 "도착"하는 곳이 아니다) 그대로 둔다.
		List<NearbyStationDto> walkCandidates = new ArrayList<>(candidates);
		walkCandidates.add(new NearbyStationDto(WALK_OPTION_NAME, centroidLat, centroidLng));

		MultiMidpointStationDto walkStation = pickBestWalkCandidate(points, walkCandidates);
		if (walkStation == null) {
			// 후보역까지 도보로 갈 수 있는 경로가 하나도 없으면(강 건너 등) 좌표 평균으로 대신한다.
			walkStation = buildWalkFallback(points, centroidLat, centroidLng);
		}

		MultiMidpointStationDto transitStation = pickBestTransitCandidate(points, candidates);
		if (transitStation == null) {
			// 후보역까지 대중교통 경로 조회가 전부 실패하면(예: 근처에 지하철역이 없음)
			// 도보 중간지점과 같은 좌표를 쓰되, "대중교통"이라고 오해하지 않도록 이름은 다르게 표시한다.
			transitStation = new MultiMidpointStationDto(
					NO_TRANSIT_CANDIDATE_NAME, walkStation.lat(), walkStation.lng(),
					walkStation.timesFromEachMinutes(), walkStation.maxTimeMinutes(), walkStation.routesFromEach(),
					List.of()
			);
		}

		List<RestaurantDto> walkRestaurants =
				kakaoRestaurantService.findNearbyRestaurants(walkStation.lng(), walkStation.lat());
		List<RestaurantDto> transitRestaurants =
				kakaoRestaurantService.findNearbyRestaurants(transitStation.lng(), transitStation.lat());

		return new MultiMidpointResultDto(
				new MultiMidpointOptionDto(walkStation, walkRestaurants),
				new MultiMidpointOptionDto(transitStation, transitRestaurants)
		);
	}

	/**
	 * 첫 번째 지점을 기준으로 나머지 지점들과 대중교통으로 연결되는지 확인한다.
	 * 단 한 곳이라도 카카오가 경로 자체를 찾지 못하면(섬처럼 육로/대중교통으로 이어지지 않는 경우)
	 * 전체를 이동 불가능하다고 보고 예외를 던진다.
	 */
	private void assertAllPointsReachable(List<PointRequest> points) {
		PointRequest reference = points.get(0);
		for (int i = 1; i < points.size(); i++) {
			PointRequest point = points.get(i);

			// 두 지점이 이미 충분히 가까우면(도보로도 갈 수 있는 거리) 굳이 대중교통 경로를
			// 확인할 필요가 없다. 카카오 대중교통 API는 거리가 너무 가까우면(직접 확인한 바로는
			// 150m는 실패, 400m는 성공) "경로 없음"(NO_RESULTS)을 주는데, 이건 "탈 만한
			// 대중교통이 없다"는 뜻이지 "이동 불가"가 아니다 — 여기서 그대로 판정하면 가까운
			// 두 지점을 "바다라서 못 감"으로 잘못 안내하게 된다.
			if (distanceMeters(reference.y(), reference.x(), point.y(), point.x()) < CLEARLY_REACHABLE_METERS) {
				continue;
			}

			try {
				kakaoTransitService.getRoute(reference.x(), reference.y(), point.x(), point.y());
			} catch (NoRouteFoundException e) {
				// 진짜로 경로가 없는 경우(섬 등)만 "이동 불가"로 판정한다. 쿼터 초과·네트워크
				// 오류 같은 다른 실패까지 여기서 잡으면 원인이 다른데도 "바다라서 안 됨"이라고
				// 잘못 안내하게 된다 — 그런 실패는 IllegalStateException 그대로 위로 던진다.
				throw new NoRouteFoundException(UNREACHABLE_MESSAGE);
			}
		}
	}

	/**
	 * 중심점 근처 후보역 3곳을 도보 기준으로 평가한다. 대중교통 후보 평가(pickBestTransitCandidate)와
	 * 같은 구조이며, 카카오 대신 Tmap 보행자 경로안내 API로 참여자별 소요시간과 실제 경로 좌표를
	 * 받아온다는 점만 다르다.
	 */
	private MultiMidpointStationDto pickBestWalkCandidate(List<PointRequest> points, List<NearbyStationDto> candidates) {
		MultiMidpointStationDto best = null;
		int bestMax = Integer.MAX_VALUE;

		for (NearbyStationDto candidate : candidates) {
			List<Integer> times = new ArrayList<>();
			List<List<RoutePointDto>> routes = new ArrayList<>();
			boolean allReachable = true;

			for (PointRequest point : points) {
				try {
					TmapRouteDto route = tmapService.getPedestrianRoute(point.x(), point.y(), candidate.lng(), candidate.lat());
					times.add(route.timeMinutes());
					routes.add(route.points());
				} catch (NoRouteFoundException e) {
					// 이 후보역까지는 걸어갈 수 없다는 뜻 -> 이 후보만 건너뛴다.
					allReachable = false;
					break;
				}
			}

			if (!allReachable) {
				continue;
			}

			int max = Collections.max(times);
			if (max < bestMax) {
				bestMax = max;
				best = new MultiMidpointStationDto(candidate.name(), candidate.lat(), candidate.lng(), times, max, routes, List.of());
			}
		}

		return best;
	}

	/**
	 * 중심점 근처 후보역 3곳을 대중교통 기준으로 평가한다. 카카오맵 대중교통 경로 조회는 한 번
	 * 호출에 소요시간과 경로 좌표를 함께 주기 때문에(ODsay처럼 시간만 조회하는 가벼운 방식과
	 * 좌표까지 포함한 방식을 나눌 필요가 없다), 평가 단계에서 받은 경로를 그대로 최종 결과에
	 * 재사용한다.
	 */
	private MultiMidpointStationDto pickBestTransitCandidate(List<PointRequest> points, List<NearbyStationDto> candidates) {
		MultiMidpointStationDto best = null;
		int bestMax = Integer.MAX_VALUE;

		for (NearbyStationDto candidate : candidates) {
			List<Integer> times = new ArrayList<>();
			List<List<RoutePointDto>> routes = new ArrayList<>();
			List<String> summaries = new ArrayList<>();
			boolean allReachable = true;

			for (PointRequest point : points) {
				try {
					TransitRouteDto route = kakaoTransitService.getRoute(point.x(), point.y(), candidate.lng(), candidate.lat());
					times.add(route.timeMinutes());
					routes.add(route.points());
					summaries.add(route.summary());
				} catch (NoRouteFoundException e) {
					// 이 후보역까지는 대중교통으로 갈 수 없다는 뜻 -> 이 후보만 건너뛴다.
					allReachable = false;
					break;
				}
			}

			if (!allReachable) {
				continue;
			}

			int max = Collections.max(times);
			if (max < bestMax) {
				bestMax = max;
				best = new MultiMidpointStationDto(candidate.name(), candidate.lat(), candidate.lng(), times, max, routes, summaries);
			}
		}

		return best;
	}

	/**
	 * 후보역까지 도보로 갈 수 있는 경로를 Tmap이 하나도 찾지 못했을 때만 쓰는 마지막 대안.
	 * 좌표 평균(직선거리) 기준 추정치이며, 경로는 참여자-중심점을 잇는 직선 2점으로만 표시한다.
	 */
	private MultiMidpointStationDto buildWalkFallback(List<PointRequest> points, double centroidLat, double centroidLng) {
		List<Integer> walkTimes = new ArrayList<>();
		List<List<RoutePointDto>> routes = new ArrayList<>();
		for (PointRequest point : points) {
			walkTimes.add(estimateWalkMinutes(distanceMeters(point.y(), point.x(), centroidLat, centroidLng)));
			routes.add(List.of(new RoutePointDto(point.y(), point.x()), new RoutePointDto(centroidLat, centroidLng)));
		}
		return new MultiMidpointStationDto(
				WALK_OPTION_NAME, centroidLat, centroidLng, walkTimes, Collections.max(walkTimes), routes, List.of()
		);
	}

	private int estimateWalkMinutes(double distanceMeters) {
		return (int) Math.ceil(distanceMeters * STRAIGHT_LINE_TO_ROAD_FACTOR / WALK_SPEED_METERS_PER_MINUTE);
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
}
