package com.siktak.exception.domain;

/** 예외 종류 (erd.md exception_cases.case_type). */
public enum CaseType {
    PAYMENT_ERROR,
    REFUND_FAILED,
    DATA_MISSING,
    MATCH_CONFLICT,
    VENUE_UNAVAILABLE,
    NOTIFICATION_FAILED,
    SAFETY_REPORT,
    /** 행사 종료 시 미체크인 노쇼 후보 (7.1, 운영자가 T5로 확정) */
    NO_SHOW_CANDIDATE
}
