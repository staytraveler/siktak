package com.siktak.matching.domain.engine;

/**
 * 이번 실행에서 배치되지 못한 후보.
 *
 * @param originSlotId 최초 배치 회차 (이번 실행 대상 중 1순위)
 * @param reasonCode   {@code deferred}면 {@code DEFERRED}, {@code failed}면 실패 원인
 * @param causeCode    미배치 원인 (UNDER_MIN_SIZE, AGE_GAP, BLOCKED_PAIR, LOW_SCORE 등)
 */
public record UnplacedCandidate(
        long applicationId,
        long userId,
        long originSlotId,
        ReasonCode reasonCode,
        ReasonCode causeCode) {
}
