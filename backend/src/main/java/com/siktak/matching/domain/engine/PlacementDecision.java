package com.siktak.matching.domain.engine;

/** 배치 로그의 결정 종류. */
public enum PlacementDecision {
    /** 최초 배치(5-3)로 임시 테이블에 들어감. */
    PLACED,
    /** 재배치(5-5) 또는 병합(5-6)으로 임시 테이블에 들어감. */
    REALLOCATED,
    /** 최종 검증(5-7) 통과로 확정 대상. */
    CONFIRMED,
    /** 모든 선택 회차에서 실패 → {@code ALTERNATIVE_OFFERED}. */
    FAILED,
    /** 이번엔 미배치, 매칭 시각 미도래 회차가 남음 → {@code WAITING} 복귀. */
    DEFERRED,
    /** 후보 추출(5-1)에서 제외. */
    EXCLUDED
}
