package com.siktak.matching.domain.engine;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 확정 후 취소 재배치 입력 (matching.md 6장). 실행 키는 호출 측이 {@code cancel-{tableId}}로 관리한다.
 *
 * @param tableId          취소가 생긴 확정 테이블
 * @param remainingMembers 취소 후 남은 멤버
 * @param openTables       같은 회차에서 자리가 남은 다른 확정 테이블
 */
public record CancelReallocationInput(
        SlotInfo slot,
        long tableId,
        List<Candidate> remainingMembers,
        List<OpenTable> openTables,
        Set<UserPair> blockedPairs,
        Set<UserPair> encounters,
        MatchingConfig config) {

    public CancelReallocationInput {
        Objects.requireNonNull(slot, "slot");
        remainingMembers = List.copyOf(remainingMembers);
        openTables = List.copyOf(openTables);
        blockedPairs = Set.copyOf(blockedPairs);
        encounters = Set.copyOf(encounters);
        Objects.requireNonNull(config, "config");
    }

    /** 이전 실행에서 확정된 테이블. */
    public record OpenTable(long tableId, long slotId, List<Candidate> members) {

        public OpenTable {
            members = List.copyOf(members);
        }
    }
}
