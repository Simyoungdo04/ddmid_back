package com.ddmid.back.service;

import com.ddmid.back.dto.NearbyStationDto;
import org.springframework.stereotype.Service;

import java.util.List;

// 후보 지점들을 도보(Tmap) 기준 소요시간으로 평가한다. 대중교통 평가(TransitMidpointEvaluator)와
// 구조는 같고, Tmap 보행자 경로안내 API로 참여자별 소요시간을 받아온다는 점만 다르다.
@Service
public class WalkMidpointEvaluator {

	private final TmapService tmapService;

	public WalkMidpointEvaluator(TmapService tmapService) {
		this.tmapService = tmapService;
	}

	// 후보들 중 "참여자 전원이 도보로 도달 가능하면서, 가장 오래 걸리는 사람의 시간이 가장
	// 작은" 후보를 고른다. 전부 도달 불가능하면 null을 돌려준다.
	public NearbyStationDto pickBest(List<Participant> participants, List<NearbyStationDto> candidates) {
		NearbyStationDto best = null;
		int bestMax = Integer.MAX_VALUE;

		for (NearbyStationDto candidate : candidates) {
			int max = 0;
			boolean allReachable = true;
			for (Participant participant : participants) {
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

	private Integer minutesOrNull(Participant participant, NearbyStationDto candidate) {
		try {
			return tmapService.getPedestrianRoute(participant.lng, participant.lat, candidate.lng(), candidate.lat())
					.timeMinutes();
		} catch (NoRouteFoundException | IllegalStateException e) {
			return null;
		}
	}
}
