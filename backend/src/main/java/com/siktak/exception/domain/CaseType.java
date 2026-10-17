package com.siktak.exception.domain;

/** 예외 종류 (erd.md exception_cases.case_type). */
public enum CaseType {
    PAYMENT_ERROR,
    REFUND_FAILED,
    DATA_MISSING,
    MATCH_CONFLICT,
    VENUE_UNAVAILABLE,
    NOTIFICATION_FAILED,
    SAFETY_REPORT
}
