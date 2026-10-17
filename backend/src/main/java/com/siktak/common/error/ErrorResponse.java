package com.siktak.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 공통 에러 응답 {code, message, details} (CLAUDE.md 9장). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, Object details) {

    public static ErrorResponse of(ErrorCode errorCode, String message, Object details) {
        return new ErrorResponse(errorCode.name(), message != null ? message : errorCode.getDefaultMessage(), details);
    }
}
