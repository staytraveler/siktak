package com.siktak.application.domain.status;

/** 참석 상태 attendance_status (PRD 12장, states.md 5장). */
public enum AttendanceStatus {
    SCHEDULED,
    ATTENDED,
    CANCELED_EARLY,
    CANCELED_LATE,
    NO_SHOW
}
