package com.siktak.matching.domain.engine;

import java.util.List;

import com.siktak.matching.domain.engine.score.ScoreDetail;

/**
 * 확정 후 취소 재배치 결과 (matching.md 6장).
 *
 * @param outcome       {@code KEPT}: 4명 이상이라 유지 / {@code DISSOLVED}: 4명 미만이라 해체하고 남은 멤버를 옮김
 * @param keptTable     {@code KEPT}일 때 점수를 다시 계산한 원래 테이블. 아니면 {@code null}
 * @param moves         다른 확정 테이블로 옮긴 멤버
 * @param updatedTables 멤버가 들어가 점수·요약이 바뀐 확정 테이블 (tableId 오름차순)
 * @param failed        자리를 못 찾은 멤버 → {@code ALTERNATIVE_OFFERED}
 */
public record CancelReallocationResult(
        Outcome outcome,
        TableUpdate keptTable,
        List<Move> moves,
        List<TableUpdate> updatedTables,
        List<UnplacedCandidate> failed,
        List<PlacementLog> placementLogs) {

    public CancelReallocationResult {
        moves = List.copyOf(moves);
        updatedTables = List.copyOf(updatedTables);
        failed = List.copyOf(failed);
        placementLogs = List.copyOf(placementLogs);
    }

    public enum Outcome {
        KEPT,
        DISSOLVED
    }

    public record Move(long applicationId, long toTableId) {
    }

    /** @param members application ID 오름차순 */
    public record TableUpdate(long tableId, List<Long> members, ScoreDetail scoreDetail, TableSummary summary) {

        public TableUpdate {
            members = List.copyOf(members);
        }
    }
}
