package com.siktak.safety.domain;

/** 신고 처리 상태 (erd.md safety_reports.report_status). */
public enum ReportStatus {
    RECEIVED,
    REVIEWING,
    RESOLVED,
    DISMISSED
}
