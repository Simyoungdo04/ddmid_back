package com.ddmid.back.service;

import com.ddmid.back.dto.RestaurantDto;
import com.ddmid.back.dto.RoutePointDto;
import com.ddmid.back.dto.TmapRouteDto;
import com.ddmid.back.dto.TransitRouteDto;
import org.springframework.stereotype.Service;

// "지도" 책임만 담당한다 - 참여자 한 명과 목적지(확정된 식당) 좌표를 받아서 실제 경로를
// 조회하고 그 결과를 참여자 객체에 채워 넣을 뿐, 방(Room)의 상태(단계 전환, 전원 확정 여부
// 등)는 전혀 모른다.
@Service
public class RouteFiller {

	private final TmapService tmapService;
	private final KakaoTransitService kakaoTransitService;

	public RouteFiller(TmapService tmapService, KakaoTransitService kakaoTransitService) {
		this.tmapService = tmapService;
		this.kakaoTransitService = kakaoTransitService;
	}

	// 도보는 Tmap이 좌표를 그대로 받아 목적지까지 정확하게 그려준다.
	public void fillWalkRoute(Participant participant, RestaurantDto destination) {
		try {
			TmapRouteDto route = tmapService.getPedestrianRoute(
					participant.lng, participant.lat, destination.lng(), destination.lat()
			);
			participant.walkTimeMinutes = route.timeMinutes();
			participant.walkRoute = route.points();
		} catch (NoRouteFoundException | IllegalStateException e) {
			// 이 사람만 도보 경로를 못 찾은 것 -> 나머지 결과는 그대로 보여준다.
		}
	}

	// 카카오 대중교통 응답은 "타는 역"~"내리는 역"까지만 준다(정확한 식당 좌표가 아님).
	// 출발지->타는 역, 내리는 역->식당 두 구간을 Tmap으로 채워서 하나의 경로로 이어 붙인다.
	public void fillTransitRoute(Participant participant, RestaurantDto destination) {
		TransitRouteDto transit;
		try {
			transit = kakaoTransitService.getRoute(participant.lng, participant.lat, destination.lng(), destination.lat());
		} catch (NoRouteFoundException | IllegalStateException e) {
			return;
		}
		if (transit.points().isEmpty()) {
			return;
		}

		RoutePointDto boardingPoint = transit.points().get(0);
		try {
			TmapRouteDto toStation = tmapService.getPedestrianRoute(
					participant.lng, participant.lat, boardingPoint.lng(), boardingPoint.lat()
			);
			participant.transitWalkToStationRoute = toStation.points();
			participant.transitWalkToStationMinutes = toStation.timeMinutes();
		} catch (NoRouteFoundException | IllegalStateException e) {
			participant.transitWalkToStationMinutes = 0;
		}

		participant.transitCoreLegs = transit.legs();

		RoutePointDto alightingPoint = transit.points().get(transit.points().size() - 1);
		try {
			TmapRouteDto fromStation = tmapService.getPedestrianRoute(
					alightingPoint.lng(), alightingPoint.lat(), destination.lng(), destination.lat()
			);
			participant.transitWalkFromStationRoute = fromStation.points();
			participant.transitWalkFromStationMinutes = fromStation.timeMinutes();
		} catch (NoRouteFoundException | IllegalStateException e) {
			participant.transitWalkFromStationMinutes = 0;
		}

		participant.transitTimeMinutes = transit.timeMinutes();
		participant.transitSummary = transit.summary();
	}
}
