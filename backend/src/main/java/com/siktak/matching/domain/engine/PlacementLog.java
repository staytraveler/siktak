package com.siktak.matching.domain.engine;

/**
 * 참가자별 배치 기록. 실행 안에서 일어난 순서대로 쌓인다.
 *
 * @param fromSlotId  재배치 출발 회차 (최초 배치 회차). 해당 없으면 {@code null}
 * @param toSlotId    들어간 회차. 해당 없으면 {@code null}
 * @param reallocStep 재배치 단계 1~4 (PRD 7.4). 재배치가 아니면 {@code null}
 * @param causeCode   미배치가 된 원인 (예: 재배치 전 {@code AGE_GAP}, matching.md 11장 I8). 해당 없으면 {@code null}
 * @param reasonText  참가자에게 보여줄 문구 ({@code reasonCode} 기준)
 */
public record PlacementLog(
        long applicationId,
        PlacementDecision decision,
        Long fromSlotId,
        Long toSlotId,
        Integer reallocStep,
        ReasonCode reasonCode,
        ReasonCode causeCode,
        String reasonText) {
}
