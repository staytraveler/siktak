package com.siktak.venue.domain.assign;

/** 식당 미배정 사유. */
public enum UnassignedReason {
    /** 같은 지역에 활성·잔여·인원 조건을 만족하는 식당이 없음 (PRD INF-02, 15장). */
    VENUE_UNAVAILABLE
}
