package com.kh.midpoint.common.exception;

// 권한이 없어서 거부되는 경우(403). 공통 부모 없이 RuntimeException을 직접 상속한다.
public class ForbiddenException extends RuntimeException {

	public ForbiddenException(String message) {
		super(message);
	}
}
