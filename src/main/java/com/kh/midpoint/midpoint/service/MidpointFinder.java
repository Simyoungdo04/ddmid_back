package com.kh.midpoint.midpoint.service;

import com.kh.midpoint.common.exception.NotFoundException;
import com.kh.midpoint.external.kakao.KakaoLocalClient;
import com.kh.midpoint.external.kakao.NearbyStationDto;
import com.kh.midpoint.participant.model.dto.ParticipantDto;
import com.kh.midpoint.restaurant.model.dto.RestaurantDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

// "지도" 책임만 담당한다 - 참여자 좌표와 이동수단(도보/대중교통)을 받아서 중간지점 좌표를
// 계산해 돌려줄 뿐, 방(Room)의 상태(참여자 목록 관리, 단계 전환 등)는 전혀 모른다. 실제
// 이동수단별 후보 평가(Tmap/카카오 API 호출)는 WalkMidpointEvaluator/TransitMidpointEvaluator에
// 위임한다.
//
// 후보역 검색(KakaoLocalClient)은 이동수단과 무관하게 항상 같은 결과라 여기서 한 번만
// 호출한다 - 평가자 쪽에 검색까지 맡기면 두 평가자가 각자 검색 로직을 중복으로 갖게 된다.
@Service
public class MidpointFinder {

	private static final int CANDIDATE_STATION_COUNT = 3;
	private static final String CENTER_CANDIDATE_NAME = "중심점";

	private final KakaoLocalClient kakaoLocalClient;
	private final WalkMidpointEvaluator walkMidpointEvaluator;
	private final TransitMidpointEvaluator transitMidpointEvaluator;

	public MidpointFinder(
			KakaoLocalClient kakaoLocalClient, WalkMidpointEvaluator walkMidpointEvaluator,
			TransitMidpointEvaluator transitMidpointEvaluator
	) {
		this.kakaoLocalClient = kakaoLocalClient;
		this.walkMidpointEvaluator = walkMidpointEvaluator;
		this.transitMidpointEvaluator = transitMidpointEvaluator;
	}

	// point: 계산된 중간지점 좌표. source: 'STATION'(후보역 선택됨) | 'CENTER'(전부 도달
	// 불가능해서 참여자 좌표 평균으로 대체됨) - ROOM.MIDPOINT_SOURCE에 그대로 저장된다.
	public record MidpointResult(NearbyStationDto point, String source) {
	}

	// 참여자 좌표 중심점 근처 지하철역 후보(최대 3곳)를 이동수단 기준 소요시간으로 평가해서,
	// "가장 오래 걸리는 사람의 시간"이 가장 작은 후보를 고른다. 도보는 후보역 대신 중심점 자체도
	// 함께 경쟁시킨다(참여자들이 가까이 몰려있으면 역보다 중심점이 더 공평한 경우가 많아서).
	// 후보 전부 "참여자 전원 도달 가능"을 만족 못 하면(거제-제주처럼 대중교통/도보로 아예
	// 이어지지 않는 경우, 중심점이 산/바다인 경우 등) findFallback()으로 넘어간다.
	public MidpointResult find(List<ParticipantDto> participants, String mode) {
		double centroidLat = participants.stream().mapToDouble(ParticipantDto::getPrefLat).average().orElseThrow();
		double centroidLng = participants.stream().mapToDouble(ParticipantDto::getPrefLng).average().orElseThrow();

		List<NearbyStationDto> stations =
				kakaoLocalClient.findNearbySubwayStations(centroidLng, centroidLat, CANDIDATE_STATION_COUNT);

		if ("walk".equals(mode)) {
			List<NearbyStationDto> candidates = new ArrayList<>(stations);
			candidates.add(new NearbyStationDto(CENTER_CANDIDATE_NAME, centroidLat, centroidLng));
			NearbyStationDto best = walkMidpointEvaluator.pickBest(participants, candidates);
			if (best == null) {
				NearbyStationDto reachableStation = walkMidpointEvaluator.pickMostReachable(participants, stations);
				return findFallback(reachableStation, centroidLat, centroidLng);
			}
			String source = CENTER_CANDIDATE_NAME.equals(best.getName()) ? "CENTER" : "STATION";
			return new MidpointResult(best, source);
		}

		NearbyStationDto best = transitMidpointEvaluator.pickBest(participants, stations);
		if (best == null) {
			NearbyStationDto reachableStation = transitMidpointEvaluator.pickMostReachable(participants, stations);
			return findFallback(reachableStation, centroidLat, centroidLng);
		}
		String source = CENTER_CANDIDATE_NAME.equals(best.getName()) ? "CENTER" : "STATION";
		return new MidpointResult(best, source);
	}

	// 후보역 전부 "참여자 전원 도달 가능"을 만족 못 한 경우의 대체 로직. 1) 그나마 가장 많은
	// 인원이 도달 가능한 후보역(reachableStation, "전원 도달"을 요구하지 않는 완화된 선택 -
	// WalkMidpointEvaluator/TransitMidpointEvaluator.pickMostReachable() 참고)을 쓰고,
	// 2) 역마저 아무도 도달 못 하면 중심점 주변 식당 검색 결과 중 첫 번째 위치를 쓰고,
	// 3) 그마저 없으면(거제-제주처럼 중심점이 바다 한복판인 경우) 중간지점을 못 찾은 것으로
	// 보고 에러를 던진다 - 방장이 참여자 위치를 다시 확인하도록 프론트에 그대로 노출된다.
	private MidpointResult findFallback(NearbyStationDto reachableStation, double centroidLat, double centroidLng) {
		if (reachableStation != null) {
			return new MidpointResult(reachableStation, "STATION");
		}

		List<RestaurantDto> nearbyRestaurants = kakaoLocalClient.findNearbyRestaurants(centroidLng, centroidLat);
		if (!nearbyRestaurants.isEmpty()) {
			RestaurantDto restaurant = nearbyRestaurants.get(0);
			return new MidpointResult(
					new NearbyStationDto(restaurant.getName(), restaurant.getLat(), restaurant.getLng()), "CENTER"
			);
		}

		throw new NotFoundException("중간지점을 찾을 수 없습니다. 참여자 위치를 확인해주세요.");
	}
}
