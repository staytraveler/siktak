package com.siktak.matching.domain.engine;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 매칭 엔진 입력 (matching.md 1장).
 *
 * <p>{@code futureSlotIds}는 따로 받지 않는다. 후보의 {@code slotChoices} 중 {@code slots}에 없는 회차를
 * 이번 실행 대상이 아닌(매칭 시각 미도래) 회차로 본다.
 * {@code openTables}(확정 후 취소 재배치, 6장)는 6.x 범위라 아직 받지 않는다.
 *
 * @param slots        이번 실행 대상 회차
 * @param candidates   대상 회차를 하나 이상 선택한 {@code WAITING} 신청
 * @param blockedPairs 차단·신고·{@code AVOID} 관계 (한쪽만 선택했어도 쌍 하나로 넣는다)
 * @param encounters   이전에 같은 테이블에서 만난 쌍
 */
public record MatchingInput(
        List<SlotInfo> slots,
        List<Candidate> candidates,
        Set<UserPair> blockedPairs,
        Set<UserPair> encounters,
        MatchingConfig config) {

    public MatchingInput {
        slots = List.copyOf(slots);
        candidates = List.copyOf(candidates);
        blockedPairs = Set.copyOf(blockedPairs);
        encounters = Set.copyOf(encounters);
        Objects.requireNonNull(config, "config");
    }
}
