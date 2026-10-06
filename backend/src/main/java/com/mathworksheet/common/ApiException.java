package com.mathworksheet.common;

import org.springframework.http.HttpStatus;

/**
 * 화면에 그대로 보여줄 한국어 메시지를 담은 API 오류. 메시지는 선생님이 이해하는 말로 쓴다(CLAUDE.md 5장).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    /** 다른 선생님이 먼저 수정함(설계서 5장 4단계 "버전 충돌") */
    public static ApiException conflict() {
        return new ApiException(HttpStatus.CONFLICT, "다른 분이 먼저 수정했습니다. 최신 내용을 다시 불러와 주세요.");
    }
}
