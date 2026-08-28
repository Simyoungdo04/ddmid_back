package com.kh.midpoint.common.exception;

// 이미 존재하는 것과 중복되는 경우(409). 공통 부모 없이 RuntimeException을 직접 상속한다.
public class DuplicateException extends RuntimeException {

	public DuplicateException(String message) {
		super(message);
	}
}
