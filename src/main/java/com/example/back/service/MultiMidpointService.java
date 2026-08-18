package com.example.back.service;

import com.example.back.dto.MultiMidpointOptionDto;
import com.example.back.dto.MultiMidpointResultDto;
import com.example.back.dto.MultiMidpointStationDto;
import com.example.back.dto.NearbyStationDto;
import com.example.back.dto.PointRequest;
import com.example.back.dto.RestaurantDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 3명 이상(N명)의 중간지점을 찾는다. ODsay는 두 지점 사이의 경로만 조회할 수 있어서
 * 2명일 때처럼 "경로 위의 역"을 쓰는 방식이 통하지 않는다. 대신:
 * 1) N개 좌표의 중심점 근처 지하철역 후보 몇 개를 뽑고
 * 2) 각 후보에 대해 N명 전원의 이동시간을 조회해서
 * 3) 그중 "가장 오래 걸리는 사람의 시간"이 가장 작은 후보를 고른다.
 */
@Service
public class MultiMidpointService {

	private static final int CANDIDATE_STATION_COUNT = 3;
	private static final String WALK_OPTION_NAME = "도보 중간지점(중심점)";
	private static final double WALK_SPEED_METERS_PER_MINUTE = 67.0; // 시속 4km 기준
	private static final double STRAIGHT_LINE_TO_ROAD_FACTOR = 1.3; // 직선거리를 실제 도보 경로 거리로 보정
	private static final double EARTH_RADIUS_METERS = 6371000;

	private final OdsayService odsayService;
	private final KakaoRestaurantService kakaoRestaurantService;

	public MultiMidpointService(OdsayService odsayService, KakaoRestaurantService kakaoRestaurantService) {
		this.odsayService = odsayService;
		this.kakaoRestaurantService = kakaoRestaurantService;
	}

	public MultiMidpointResultDto findMidpoint(List<PointRequest> points) {
		if (points.size() < 3) {
			throw new IllegalArgumentException("3명 이상일 때 쓰는 기능입니다.");
		}

		double centroidLat = points.stream().mapToDouble(PointRequest::y).average().orElseThrow();
		double centroidLng = points.stream().mapToDouble(PointRequest::x).average().orElseThrow();

		MultiMidpointStationDto walkStation = buildWalkOption(points, centroidLat, centroidLng);

		List<NearbyStationDto> candidates =
				kakaoRestaurantService.findNearbySubwayStations(centroidLng, centroidLat, CANDIDATE_STATION_COUNT);

		MultiMidpointStationDto transitStation = pickBestTransitCandidate(points, candidates);
		if (transitStation == null) {
			// 후보역까지 대중교통 경로 조회가 전부 실패하면 도보 중간지점으로 대체한다.
			transitStation = walkStation;
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

	private MultiMidpointStationDto buildWalkOption(List<PointRequest> points, double centroidLat, double centroidLng) {
		List<Integer> walkTimes = points.stream()
				.map(p -> estimateWalkMinutes(distanceMeters(p.y(), p.x(), centroidLat, centroidLng)))
				.toList();
		return new MultiMidpointStationDto(
				WALK_OPTION_NAME, centroidLat, centroidLng, walkTimes, Collections.max(walkTimes)
		);
	}

	private MultiMidpointStationDto pickBestTransitCandidate(List<PointRequest> points, List<NearbyStationDto> candidates) {
		MultiMidpointStationDto best = null;
		int bestMax = Integer.MAX_VALUE;

		for (NearbyStationDto candidate : candidates) {
			List<Integer> times = new ArrayList<>();
			boolean allReachable = true;

			for (PointRequest point : points) {
				try {
					times.add(odsayService.getTravelTimeMinutes(point.x(), point.y(), candidate.lng(), candidate.lat()));
				} catch (IllegalStateException e) {
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
				best = new MultiMidpointStationDto(candidate.name(), candidate.lat(), candidate.lng(), times, max);
			}
		}

		return best;
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
