package com.kh.midpoint.midpoint.service;

import com.kh.midpoint.common.exception.NotFoundException;
import com.kh.midpoint.external.kakao.KakaoTransitClient;
import com.kh.midpoint.external.kakao.NearbyStationDto;
import com.kh.midpoint.participant.model.dto.ParticipantDto;
import org.springframework.stereotype.Service;

import java.util.List;

// 후보 지점들을 대중교통(카카오) 기준 소요시간으로 평가한다. 도보 평가(WalkMidpointEvaluator)와
// 구조는 같고, 카카오 대중교통 경로 조회 API로 참여자별 소요시간을 받아온다는 점만 다르다.
@Service
public class TransitMidpointEvaluator {

	private final KakaoTransitClient kakaoTransitClient;

	public TransitMidpointEvaluator(KakaoTransitClient kakaoTransitClient) {
		this.kakaoTransitClient = kakaoTransitClient;
	}

	// 후보들 중 "참여자 전원이 대중교통으로 도달 가능하면서, 가장 오래 걸리는 사람의 시간이
	// 가장 작은" 후보를 고른다. 전부 도달 불가능하면 null을 돌려준다.
	public NearbyStationDto pickBest(List<ParticipantDto> participants, List<NearbyStationDto> candidates) {
		NearbyStationDto best = null;
		int bestMax = Integer.MAX_VALUE;

		for (NearbyStationDto candidate : candidates) {
			int max = 0;
			boolean allReachable = true;
			for (ParticipantDto participant : participants) {
				Integer minutes = minutesOrNull(participant, candidate);
				if (minutes == null) {
					allReachable = false;
					break;
				}
				max = Math.max(max, minutes);
			}

			if (allReachable && max < bestMax) {
				bestMax = max;
				best = candidate;
			}
		}

		return best;
	}

	// pickBest가 null을 돌려준 경우(전원 도달 가능한 후보가 하나도 없음 - 참여자들이 대중
	// 교통으로 이어지지 않는 곳에 흩어져 있는 경우 등)에 쓰는 완화된 선택. "전원 도달"을
	// 요구하지 않고, 그나마 가장 많은 인원이 도달 가능한 역을 고른다(동률이면 그 인원 기준
	// 최대 소요시간이 짧은 쪽). 아무도 도달하지 못하는 역만 있으면 null.
	public NearbyStationDto pickMostReachable(List<ParticipantDto> participants, List<NearbyStationDto> candidates) {
		NearbyStationDto best = null;
		int bestReachableCount = 0;
		int bestMax = Integer.MAX_VALUE;

		for (NearbyStationDto candidate : candidates) {
			int reachableCount = 0;
			int max = 0;
			for (ParticipantDto participant : participants) {
				Integer minutes = minutesOrNull(participant, candidate);
				if (minutes != null) {
					reachableCount++;
					max = Math.max(max, minutes);
				}
			}

			if (reachableCount == 0) {
				continue;
			}
			if (reachableCount > bestReachableCount || (reachableCount == bestReachableCount && max < bestMax)) {
				bestReachableCount = reachableCount;
				bestMax = max;
				best = candidate;
			}
		}

		return best;
	}

	private Integer minutesOrNull(ParticipantDto participant, NearbyStationDto candidate) {
		try {
			return kakaoTransitClient.getRoute(participant.getPrefLng(), participant.getPrefLat(), candidate.getLng(), candidate.getLat())
					.getTimeMinutes();
		} catch (NotFoundException e) {
			return null; // 이 후보까지는 대중교통으로 갈 수 없다는 뜻 -> 이 후보만 건너뛴다.
		}
	}
}
