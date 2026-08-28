package com.kh.midpoint.common.exception;

// 인증되지 않은 경우(401). 공통 부모 없이 RuntimeException을 직접 상속한다.
public class UnauthorizedException extends RuntimeException {

	public UnauthorizedException(String message) {
		super(message);
	}
}
