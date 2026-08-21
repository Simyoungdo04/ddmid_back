package com.ddmid.back.service;

/**
 * 두 지점이 지리적으로 이동 수단(대중교통/도보)으로 연결되지 않을 때만 던진다.
 * API 쿼터 초과, 네트워크 오류, 인증 실패 같은 다른 실패와 구분하기 위한 전용 예외다 —
 * 이 예외가 아닌 다른 실패까지 "이동 불가(바다 등)"로 뭉뚱그리면 진짜 원인을 알 수 없게 된다.
 */
public class NoRouteFoundException extends RuntimeException {
	public NoRouteFoundException(String message) {
		super(message);
	}
}
