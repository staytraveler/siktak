package com.siktak.matching.domain;

/** 확정 후 취소 재배치에서 들어갈 테이블이 없어 구성원이 테이블에서 빠졌다 (대체 일정 선택으로 넘어간다). 참석 예정을 없앤다. */
public record TableMemberDroppedEvent(Long applicationId, Long fromTableId) {
}
