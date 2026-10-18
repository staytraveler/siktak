package com.siktak.safety.domain;

/** 신고 사유. [추측] PRD에 사유 목록이 없어 일반적인 분류로 둔다 — 완료 보고 "PM 확인 필요". */
public enum ReportReason {
    HARASSMENT,
    INAPPROPRIATE_BEHAVIOR,
    PRIVACY_VIOLATION,
    SAFETY_THREAT,
    OTHER
}
