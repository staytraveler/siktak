package com.siktak.matching.domain;

/** 신청이 테이블 구성원으로 확정됐다 (M3·M5). 참석(T1) 생성 등 후속 처리가 같은 트랜잭션에서 듣는다. */
public record TableMemberConfirmedEvent(Long diningTableId, Long applicationId, Long userId) {
}
