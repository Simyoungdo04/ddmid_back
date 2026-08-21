package com.ddmid.back.dto;

import java.util.List;

public record TmapRouteDto(int timeMinutes, List<RoutePointDto> points) {
}
