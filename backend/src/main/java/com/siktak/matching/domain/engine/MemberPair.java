package com.siktak.matching.domain.engine;

/** 테이블 안의 신청 쌍 (application ID 오름차순). */
public record MemberPair(long applicationIdA, long applicationIdB) {
}
