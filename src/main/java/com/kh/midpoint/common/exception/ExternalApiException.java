package com.kh.midpoint.common.exception;

// 외부 API(카카오/Tmap) 호출 자체가 실패한 경우(502). 공통 부모 없이 RuntimeException을 직접 상속한다.
public class ExternalApiException extends RuntimeException {

	public ExternalApiException(String message) {
		super(message);
	}
}
