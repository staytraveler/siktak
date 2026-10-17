package com.siktak.matching.domain;

/** 배치 로그의 결정 (erd.md match_placement_logs.decision). */
public enum MatchDecision {
    PLACED,
    REALLOCATED,
    CONFIRMED,
    FAILED
}
