package com.siktak.matching.domain;

/**
 * 배치 로그의 결정 (erd.md match_placement_logs.decision).
 * DEFERRED·EXCLUDED는 엔진(v2) 로그를 그대로 남기려고 추가했다 (matching.domain.engine.PlacementDecision과 같은 값).
 */
public enum MatchDecision {
    PLACED,
    REALLOCATED,
    CONFIRMED,
    FAILED,
    DEFERRED,
    EXCLUDED
}
