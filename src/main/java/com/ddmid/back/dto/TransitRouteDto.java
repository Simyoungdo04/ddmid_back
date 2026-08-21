package com.ddmid.back.dto;

import java.util.List;

public record TransitRouteDto(int timeMinutes, List<RoutePointDto> points, String summary) {
}
