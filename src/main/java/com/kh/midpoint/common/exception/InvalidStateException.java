package com.kh.midpoint.common.exception;

// 현재 상태에서는 처리할 수 없는 요청인 경우(400). 공통 부모 없이 RuntimeException을 직접 상속한다.
public class InvalidStateException extends RuntimeException {

	public InvalidStateException(String message) {
		super(message);
	}
}
