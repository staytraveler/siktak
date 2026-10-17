package com.siktak.matching.domain;

/** 매칭 실패 후 참가자의 선택 (erd.md match_resolutions.choice, PRD STA-04·05). */
public enum ResolutionChoice {
    /** 추천 일정으로 이동 (M7) */
    TRANSFER,
    /** 이용권 보관 (M8, 1회권은 크레딧 발급) */
    KEEP,
    /** 환불 (M8 + P6) */
    REFUND
}
