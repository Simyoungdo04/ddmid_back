package com.ddmid.back.service;

import com.ddmid.back.dto.NearbyStationDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

// "지도" 책임만 담당한다 - 참여자 좌표와 이동수단(도보/대중교통)을 받아서 중간지점 좌표를
// 계산해 돌려줄 뿐, 방(Room)의 상태(참여자 목록 관리, 단계 전환 등)는 전혀 모른다. 실제
// 이동수단별 후보 평가(Tmap/카카오 API 호출)는 WalkMidpointEvaluator/TransitMidpointEvaluator에
// 위임한다.
//
// 후보역 검색(KakaoStationService)은 이동수단과 무관하게 항상 같은 결과라 여기서 한 번만
// 호출한다 - 평가자 쪽에 검색까지 맡기면 그쪽 API 호출 횟수는 그대로지만(도보/대중교통 중
// 하나만 평가하므로), 두 평가자가 각자 검색 로직을 중복으로 갖게 되어 실제로 나눌 이유가
// 없는 코드가 두 벌 생긴다. 그래서 검색은 조정자인 여기서 한 번만 하고, 평가만 나눴다.
@Service
public class MidpointFinder {

	private static final int CANDIDATE_STATION_COUNT = 3;
	private static final String CENTER_CANDIDATE_NAME = "중심점";

	private final KakaoStationService kakaoStationService;
	private final WalkMidpointEvaluator walkMidpointEvaluator;
	private final TransitMidpointEvaluator transitMidpointEvaluator;

	public MidpointFinder(
			KakaoStationService kakaoStationService, WalkMidpointEvaluator walkMidpointEvaluator,
			TransitMidpointEvaluator transitMidpointEvaluator
	) {
		this.kakaoStationService = kakaoStationService;
		this.walkMidpointEvaluator = walkMidpointEvaluator;
		this.transitMidpointEvaluator = transitMidpointEvaluator;
	}

	// 참여자 좌표 중심점 근처 지하철역 후보(최대 3곳)를 이동수단 기준 소요시간으로 평가해서,
	// "가장 오래 걸리는 사람의 시간"이 가장 작은 후보를 고른다. 도보는 후보역 대신 중심점 자체도
	// 함께 경쟁시킨다(참여자들이 가까이 몰려있으면 역보다 중심점이 더 공평한 경우가 많아서).
	// 후보 전부 도달 불가능하면(강 건너 등) 중심점 좌표를 그대로 돌려준다.
	public NearbyStationDto find(List<Participant> participants, String mode) {
		double centroidLat = participants.stream().mapToDouble(p -> p.lat).average().orElseThrow();
		double centroidLng = participants.stream().mapToDouble(p -> p.lng).average().orElseThrow();

		List<NearbyStationDto> stations =
				kakaoStationService.findNearbySubwayStations(centroidLng, centroidLat, CANDIDATE_STATION_COUNT);

		NearbyStationDto best;
		if ("walk".equals(mode)) {
			List<NearbyStationDto> candidates = new ArrayList<>(stations);
			candidates.add(new NearbyStationDto(CENTER_CANDIDATE_NAME, centroidLat, centroidLng));
			best = walkMidpointEvaluator.pickBest(participants, candidates);
		} else {
			best = transitMidpointEvaluator.pickBest(participants, stations);
		}

		return best != null ? best : new NearbyStationDto(CENTER_CANDIDATE_NAME, centroidLat, centroidLng);
	}
}
