package com.siktak.application.domain.status;

/** 매칭 상태 match_status (PRD 12장, states.md 4장). */
public enum MatchStatus {
    WAITING,
    MATCHING,
    REALLOCATING,
    CONFIRMED,
    ALTERNATIVE_OFFERED,
    TRANSFERRED,
    NO_MATCH,
    EXCEPTION
}
