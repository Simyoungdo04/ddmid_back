package com.kh.midpoint.common.response;

// 모든 API 응답을 이 모양으로 감싼다: 성공 여부 + 메시지 + 실제 데이터.
public record ApiResponse<T>(boolean success, String message, T data) {

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, null, data);
	}

	public static <T> ApiResponse<T> created(String message, T data) {
		return new ApiResponse<>(true, message, data);
	}

	public static ApiResponse<Void> updated() {
		return new ApiResponse<>(true, null, null);
	}

	public static ApiResponse<Void> fail(String message) {
		return new ApiResponse<>(false, message, null);
	}
}
