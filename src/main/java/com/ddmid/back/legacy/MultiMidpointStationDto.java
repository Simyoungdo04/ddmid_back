package com.ddmid.back.legacy;

import com.ddmid.back.dto.RoutePointDto;

import java.util.List;

public record MultiMidpointStationDto(
		String name,
		Double lat,
		Double lng,
		List<Integer> timesFromEachMinutes,
		int maxTimeMinutes,
		List<List<RoutePointDto>> routesFromEach,
		List<String> transitSummariesFromEach,
		// 대중교통 옵션에서만 채워진다. 카카오 대중교통 경로는 "실제로 타는 역/정류장"부터
		// "실제로 내리는 역/정류장"까지만 주고, 참여자 출발지점 -> 타는 역, 내리는 역 -> 중간지점
		// 두 구간의 도보는 안 준다(경로의 첫/마지막 좌표가 요청한 출발/도착 좌표와 실제로 다르다 -
		// 직접 curl로 확인함). 그래서 이 두 구간을 Tmap으로 따로 채운다. 참여자가 역 바로
		// 앞(110m 이내)에서 출발/도착하면 0분/빈 리스트다. 도보 옵션에서는 어차피
		// timesFromEachMinutes 자체가 도보 시간이라 둘 다 비어있는 리스트로 둔다.
		List<Integer> walkToStationTimesFromEachMinutes,
		List<List<RoutePointDto>> walkToStationRoutesFromEach,
		List<Integer> walkFromStationTimesFromEachMinutes,
		List<List<RoutePointDto>> walkFromStationRoutesFromEach
) {
}
