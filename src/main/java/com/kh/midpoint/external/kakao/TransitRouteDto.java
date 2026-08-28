package com.kh.midpoint.external.kakao;

import com.kh.midpoint.external.tmap.RoutePointDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

// 카카오 대중교통 경로는 출발 좌표에서 실제로 타는 역/정류장까지의 도보 구간을 주지 않는다
// (첫 구간부터 바로 지하철/버스 구간으로 시작한다 - 이미 그 역에 있다고 가정). 그래서
// points.get(0)이 곧 "타는 역"의 좌표다 - 이 좌표까지 걸어가는 도보 시간은 TmapRouteClient로
// 따로 구해야 한다(RouteFiller 참고).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TransitRouteDto {
	private int timeMinutes;
	private List<RoutePointDto> points;
	private String summary;
	private List<TransitLegDto> legs;
}
