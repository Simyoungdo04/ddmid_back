package com.kh.midpoint.external.tmap;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TmapRouteDto {
	private int timeMinutes;
	private List<RoutePointDto> points;
}
