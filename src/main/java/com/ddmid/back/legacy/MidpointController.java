package com.ddmid.back.legacy;

import com.ddmid.back.service.NoRouteFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class MidpointController {

	private final MultiMidpointService multiMidpointService;

	public MidpointController(MultiMidpointService multiMidpointService) {
		this.multiMidpointService = multiMidpointService;
	}

	@PostMapping("/api/midpoint")
	public ResponseEntity<?> midpoint(@RequestBody List<PointRequest> points) {
		try {
			MultiMidpointResultDto result = multiMidpointService.findMidpoint(points);
			return ResponseEntity.ok(result);
		} catch (NoRouteFoundException | IllegalArgumentException e) {
			// 입력값 문제이거나(인원수 등) 지리적으로 진짜 이동 불가능한 경우 -> 사용자가 알아야 할 실패
			return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
		} catch (IllegalStateException e) {
			// ODsay/Tmap 쿼터 초과, 네트워크 오류 등 외부 API 자체의 문제 -> 사용자 입력과 무관한 실패
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
		}
	}
}
