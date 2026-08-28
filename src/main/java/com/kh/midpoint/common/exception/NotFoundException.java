package com.kh.midpoint.common.exception;

// 요청한 대상을 찾을 수 없는 경우(404). 공통 부모 없이 RuntimeException을 직접 상속한다.
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}
}
