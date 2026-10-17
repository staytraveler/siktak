package com.siktak.matching.domain.engine;

import java.util.List;

import com.siktak.matching.domain.engine.score.ScoreDetail;

/**
 * 확정 대상 테이블.
 *
 * @param members application ID 오름차순
 */
public record ProposedTable(
        long slotId,
        List<Long> members,
        double totalScore,
        ScoreDetail scoreDetail,
        TableSummary summary) {

    public ProposedTable {
        members = List.copyOf(members);
    }
}
