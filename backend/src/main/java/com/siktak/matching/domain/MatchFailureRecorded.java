package com.siktak.matching.domain;

/** 매칭 실패(M6) 사유와 대체 일정 추천이 신청에 기록된 직후 발행한다 (알림의 계기). */
public record MatchFailureRecorded(Long applicationId) {
}
