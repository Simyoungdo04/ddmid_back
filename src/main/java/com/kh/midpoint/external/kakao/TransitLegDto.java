package com.kh.midpoint.external.kakao;

import com.kh.midpoint.external.tmap.RoutePointDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

// 대중교통 경로 안의 한 구간(도보 환승/버스/지하철). type은 카카오 응답의 step
// properties.type을 그대로 쓴다 ("WALKING", "BUS", "SUBWAY" 등) - 지도에서 버스/지하철을
// 다른 색으로 그리기 위해 구간별로 나눠서 둔다. guidance는 그 구간의 카카오 안내 문구
// (예: "간선 340외 11대 (정류장1 > 정류장2)")인데, 여러 노선이 겹치면 "외 N대"로 뭉뚱그려서
// 온다 - vehicles는 그 구간을 지나는 노선을 전부 개별로 담은 목록이다(같은 응답 안에
// properties.vehicles로 이미 들어있어서 추가 API 호출 없이 만들 수 있다).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TransitLegDto {
	private String type;
	private List<RoutePointDto> points;
	private String guidance;
	private List<String> vehicles;
}
