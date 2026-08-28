package com.kh.midpoint.route.model.dao;

import com.kh.midpoint.external.tmap.RoutePointDto;
import com.kh.midpoint.route.model.dto.ParticipantRouteDto;
import com.kh.midpoint.route.model.vo.ParticipantRoute;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ParticipantRouteMapper {

	// ROUTE_ID 시퀀스 다음 값 - 불변 VO를 빌더로 만들기 전에 미리 받아온다.
	Long nextRouteId();

	// 방장이 식당을 바꿔 다시 확정하는 경우를 대비해, resolve()마다 새로 채우기 전에
	// 이 참여자의 기존 경로를 지운다. mode별로 따로 지운다 - 도보/대중교통을 각각 채우는
	// fillWalkRoute()/fillTransitRoute()가 서로의 결과를 지우면 안 되기 때문이다.
	void deleteByParticipantIdAndMode(@Param("participantId") Long participantId, @Param("mode") String mode);

	// PARTICIPANT_ROUTE_POINT는 FK ON DELETE CASCADE라 이것만 지우면 좌표도 같이 지워진다.
	void deleteByRoomId(@Param("roomId") Long roomId);

	void insertLeg(ParticipantRoute route);

	void insertPoints(@Param("routeId") Long routeId, @Param("points") List<RoutePointDto> points);

	// 구간 목록(순서대로) - 좌표는 구간마다 findPointsByRouteId()로 따로 가져온다.
	List<ParticipantRouteDto> findLegsByParticipantId(@Param("participantId") Long participantId);

	List<RoutePointDto> findPointsByRouteId(@Param("routeId") Long routeId);
}
