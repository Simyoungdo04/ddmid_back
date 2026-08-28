package com.kh.midpoint.route.service;

import com.kh.midpoint.common.exception.NotFoundException;
import com.kh.midpoint.external.kakao.KakaoTransitClient;
import com.kh.midpoint.external.kakao.TransitLegDto;
import com.kh.midpoint.external.kakao.TransitRouteDto;
import com.kh.midpoint.external.tmap.RoutePointDto;
import com.kh.midpoint.external.tmap.TmapRouteClient;
import com.kh.midpoint.external.tmap.TmapRouteDto;
import com.kh.midpoint.participant.model.dto.ParticipantDto;
import com.kh.midpoint.restaurant.model.dto.RestaurantDto;
import com.kh.midpoint.route.model.dao.ParticipantRouteMapper;
import com.kh.midpoint.route.model.vo.ParticipantRoute;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// "지도" 책임만 담당한다 - 참여자 한 명과 목적지(확정된 식당) 좌표를 받아서 실제 경로를
// 조회하고 PARTICIPANT_ROUTE(+POINT)에 저장할 뿐, 방(Room)의 상태(단계 전환 등)는 모른다.
// PARTICIPANT_ROUTE 스키마는 아직 팀 확정 전 제안 상태다
// (docs/2026-08-26_작업자_DB-스키마-추가검토.md 6번 참고).
@Service
public class RouteFiller {

	private final TmapRouteClient tmapRouteClient;
	private final KakaoTransitClient kakaoTransitClient;
	private final ParticipantRouteMapper participantRouteMapper;

	public RouteFiller(
			TmapRouteClient tmapRouteClient, KakaoTransitClient kakaoTransitClient,
			ParticipantRouteMapper participantRouteMapper
	) {
		this.tmapRouteClient = tmapRouteClient;
		this.kakaoTransitClient = kakaoTransitClient;
		this.participantRouteMapper = participantRouteMapper;
	}

	// 도보는 Tmap이 좌표를 그대로 받아 목적지까지 정확하게 그려준다.
	@Transactional
	public void fillWalkRoute(Long roomId, ParticipantDto participant, RestaurantDto destination) {
		participantRouteMapper.deleteByParticipantIdAndMode(participant.getParticipantId(), "walk");
		try {
			TmapRouteDto route = tmapRouteClient.getPedestrianRoute(
					participant.getPrefLng(), participant.getPrefLat(), destination.getLng(), destination.getLat()
			);
			saveLeg(roomId, participant.getParticipantId(), "walk", 0, "WALK", route.getTimeMinutes(), null, null, route.getPoints());
		} catch (NotFoundException e) {
			// 이 사람만 도보 경로를 못 찾은 것 -> 나머지 결과는 그대로 보여준다.
		}
	}

	// 카카오 대중교통 응답은 "타는 역"~"내리는 역"까지만 준다(정확한 식당 좌표가 아님).
	// 출발지->타는 역, 내리는 역->식당 두 구간을 Tmap으로 채워서 하나의 경로로 이어 붙인다.
	@Transactional
	public void fillTransitRoute(Long roomId, ParticipantDto participant, RestaurantDto destination) {
		// 여기서 삼키면 RoomService의 log.warn까지 도달을 못 해서 실패 원인을 전혀 알 수
		// 없게 된다 - 이 최상위 호출 실패는 그대로 던져서 RoomService가 로그를 남기게 한다.
		TransitRouteDto transit = kakaoTransitClient.getRoute(participant.getPrefLng(), participant.getPrefLat(), destination.getLng(), destination.getLat());
		if (transit.getPoints().isEmpty()) {
			return;
		}

		participantRouteMapper.deleteByParticipantIdAndMode(participant.getParticipantId(), "transit");
		int legOrder = 0;

		RoutePointDto boardingPoint = transit.getPoints().get(0);
		try {
			TmapRouteDto toStation = tmapRouteClient.getPedestrianRoute(
					participant.getPrefLng(), participant.getPrefLat(), boardingPoint.getLng(), boardingPoint.getLat()
			);
			// legType을 'WALK_TO_STATION'/'WALK_FROM_STATION'으로 구분해둔다 - 둘 다 그냥
			// 'WALK'로 저장하면 나중에 조회할 때(RoomService) 어느 쪽이 출발 구간이고 어느
			// 쪽이 도착 구간인지 순서로 추측해야 해서 헷갈린다.
			saveLeg(roomId, participant.getParticipantId(), "transit", legOrder, "WALK_TO_STATION", toStation.getTimeMinutes(), null, null, toStation.getPoints());
		} catch (NotFoundException e) {
			// 이 구간만 도보 경로를 못 찾은 것 -> 순서 번호만 그대로 넘어간다.
		}
		legOrder++;

		// 카카오는 대중교통 구간별 개별 소요시간을 주지 않고 전체 합계(transit.getTimeMinutes())만
		// 준다 - 첫 대중교통 구간에 전체 시간을 몰아서 저장하고 나머지 구간은 0으로 둔다.
		// 지도에 구간별로 그리는 데는 지장 없고(좌표/안내문구/노선은 구간마다 있음), 총
		// 소요시간을 다시 계산할 때만 이 규칙을 알아야 한다.
		boolean firstCoreLeg = true;
		for (TransitLegDto leg : transit.getLegs()) {
			int minutes = firstCoreLeg ? transit.getTimeMinutes() : 0;
			firstCoreLeg = false;
			String vehicles = leg.getVehicles().isEmpty() ? null : String.join(",", leg.getVehicles());
			saveLeg(roomId, participant.getParticipantId(), "transit", legOrder++, leg.getType(), minutes, leg.getGuidance(), vehicles, leg.getPoints());
		}

		RoutePointDto alightingPoint = transit.getPoints().get(transit.getPoints().size() - 1);
		try {
			TmapRouteDto fromStation = tmapRouteClient.getPedestrianRoute(
					alightingPoint.getLng(), alightingPoint.getLat(), destination.getLng(), destination.getLat()
			);
			saveLeg(roomId, participant.getParticipantId(), "transit", legOrder, "WALK_FROM_STATION", fromStation.getTimeMinutes(), null, null, fromStation.getPoints());
		} catch (NotFoundException e) {
			// 이 구간만 도보 경로를 못 찾은 것 -> 나머지 결과는 그대로 보여준다.
		}
	}

	private void saveLeg(
			Long roomId, Long participantId, String mode, int legOrder, String legType, int timeMinutes,
			String guidance, String vehicles, List<RoutePointDto> points
	) {
		if (points.isEmpty()) {
			return;
		}
		Long routeId = participantRouteMapper.nextRouteId();
		ParticipantRoute route = ParticipantRoute.builder()
				.routeId(routeId)
				.participantId(participantId)
				.roomId(roomId)
				.mode(mode)
				.legOrder(legOrder)
				.legType(legType)
				.timeMinutes(timeMinutes)
				.guidance(guidance)
				.vehicles(vehicles)
				.build();
		participantRouteMapper.insertLeg(route);
		participantRouteMapper.insertPoints(routeId, points);
	}
}
