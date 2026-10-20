package com.siktak.matching.domain;

/** 확정 후 취소에 따른 재배치로 구성원이 다른 확정 테이블로 옮겨졌다 (matching.md 6장). 참석 정보의 테이블을 맞춘다. */
public record TableMemberMovedEvent(Long applicationId, Long fromTableId, Long toTableId) {
}
