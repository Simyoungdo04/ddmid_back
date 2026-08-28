package com.kh.midpoint.common.exception;

import com.kh.midpoint.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 모든 컨트롤러의 예외를 한 곳에서 잡는다. 예외 클래스는 성격별 6종(NotFound/Duplicate/
// Forbidden/Unauthorized/InvalidState/ExternalApi)이 공통 부모 없이 RuntimeException을
// 각자 직접 상속하므로, 상태코드별로 핸들러를 따로 둔다.
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(e.getMessage()));
	}

	@ExceptionHandler(DuplicateException.class)
	public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail(e.getMessage()));
	}

	@ExceptionHandler(ForbiddenException.class)
	public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail(e.getMessage()));
	}

	@ExceptionHandler(UnauthorizedException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException e) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail(e.getMessage()));
	}

	@ExceptionHandler(InvalidStateException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidState(InvalidStateException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(e.getMessage()));
	}

	@ExceptionHandler(ExternalApiException.class)
	public ResponseEntity<ApiResponse<Void>> handleExternalApi(ExternalApiException e) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.fail(e.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
		// 예상 못한 예외는 원인을 알 수 없으면 디버깅이 불가능하니 서버 로그에 남긴다.
		log.error("처리되지 않은 예외", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail("서버 오류가 발생했습니다."));
	}
}
