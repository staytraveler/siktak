package com.siktak.matching.domain.engine;

import static com.siktak.matching.domain.engine.constraint.HardConstraints.TABLE_MAX;
import static com.siktak.matching.domain.engine.constraint.HardConstraints.TABLE_MIN;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.siktak.matching.domain.engine.CancelReallocationInput.OpenTable;
import com.siktak.matching.domain.engine.CancelReallocationResult.Move;
import com.siktak.matching.domain.engine.CancelReallocationResult.Outcome;
import com.siktak.matching.domain.engine.CancelReallocationResult.TableUpdate;
import com.siktak.matching.domain.engine.constraint.HardConstraints;
import com.siktak.matching.domain.engine.score.TableScorer;

/**
 * 확정 후 취소 재배치 (matching.md 6장, PRD 15장). 순수 함수.
 *
 * <ul>
 *   <li>남은 멤버가 4명 이상이면 테이블을 유지하고 점수만 다시 계산한다 (E22)</li>
 *   <li>4명 미만이면 남은 멤버를 application ID 순으로, 같은 회차의 자리 남은 확정 테이블 중 점수 상승이 가장 큰 곳에
 *       넣는다. 필수 조건과 최소 점수를 그대로 적용하고, 자리를 못 찾으면 실패 (E21)</li>
 * </ul>
 */
final class CancelReallocation {

    private static final double EPS = 1e-9;
    private static final Comparator<Candidate> BY_ID = Comparator.comparingLong(Candidate::applicationId);

    private CancelReallocation() {
    }

    static CancelReallocationResult run(CancelReallocationInput in) {
        long slotId = in.slot().id();
        List<Candidate> remaining = in.remainingMembers().stream().sorted(BY_ID).toList();
        List<PlacementLog> logs = new ArrayList<>();

        if (remaining.size() >= TABLE_MIN) {
            return new CancelReallocationResult(Outcome.KEPT, update(in, in.tableId(), remaining), List.of(), List.of(),
                    List.of(), logs);
        }

        // 같은 회차, 원래 테이블이 아닌 확정 테이블만. tableId 순으로 순회한다.
        TreeMap<Long, List<Candidate>> open = new TreeMap<>();
        for (OpenTable t : in.openTables()) {
            if (t.slotId() == slotId && t.tableId() != in.tableId()) {
                open.put(t.tableId(), t.members().stream().sorted(BY_ID).toList());
            }
        }

        List<Move> moves = new ArrayList<>();
        List<UnplacedCandidate> failed = new ArrayList<>();
        TreeMap<Long, List<Candidate>> changed = new TreeMap<>();
        for (Candidate c : remaining) {
            Long target = bestTarget(in, c, open);
            if (target == null) {
                // 동석자 취소로 인원이 부족해졌고 들어갈 테이블도 없다.
                ReasonCode reason = ReasonCode.UNDER_MIN_SIZE;
                failed.add(new UnplacedCandidate(c.applicationId(), c.userId(), slotId, reason, reason));
                logs.add(log(c, PlacementDecision.FAILED, slotId, null, null, reason, reason));
                continue;
            }
            List<Candidate> next = with(open.get(target), c);
            open.put(target, next);
            changed.put(target, next);
            moves.add(new Move(c.applicationId(), target));
            ReasonCode reason = ReasonCode.forReallocStep(1);
            logs.add(log(c, PlacementDecision.REALLOCATED, slotId, slotId, 1, reason, ReasonCode.UNDER_MIN_SIZE));
            logs.add(log(c, PlacementDecision.CONFIRMED, slotId, slotId, 1, reason, ReasonCode.UNDER_MIN_SIZE));
        }

        List<TableUpdate> updates = new ArrayList<>();
        for (Map.Entry<Long, List<Candidate>> e : changed.entrySet()) {
            updates.add(update(in, e.getKey(), e.getValue()));
        }
        return new CancelReallocationResult(Outcome.DISSOLVED, null, moves, updates, failed, logs);
    }

    /** 인원 < 6, 필수 조건 유지, 넣은 후 최소 점수 이상인 테이블 중 점수 상승이 가장 큰 곳. 동점이면 tableId가 작은 곳. */
    private static Long bestTarget(CancelReallocationInput in, Candidate c, TreeMap<Long, List<Candidate>> open) {
        Long best = null;
        double bestDelta = 0;
        for (Map.Entry<Long, List<Candidate>> e : open.entrySet()) {
            List<Candidate> members = e.getValue();
            if (members.size() >= TABLE_MAX || !c.chose(in.slot().id())) {
                continue;
            }
            List<Candidate> next = with(members, c);
            if (!HardConstraints.compatible(next, in.blockedPairs())) {
                continue;
            }
            double score = TableScorer.total(next, in.encounters(), in.wantAgain(), in.config().weights());
            if (score < in.config().minScore() - EPS) {
                continue;
            }
            double delta = score - TableScorer.total(members, in.encounters(), in.wantAgain(), in.config().weights());
            if (best == null || delta > bestDelta + EPS) {
                best = e.getKey();
                bestDelta = delta;
            }
        }
        return best;
    }

    private static TableUpdate update(CancelReallocationInput in, long tableId, List<Candidate> members) {
        return new TableUpdate(
                tableId,
                members.stream().map(Candidate::applicationId).toList(),
                TableScorer.score(members, in.encounters(), in.wantAgain(), in.config().weights()),
                TableSummarizer.summarize(in.slot().id(), members, in.encounters(), in.blockedPairs()));
    }

    private static List<Candidate> with(List<Candidate> members, Candidate c) {
        List<Candidate> next = new ArrayList<>(members);
        next.add(c);
        next.sort(BY_ID);
        return List.copyOf(next);
    }

    private static PlacementLog log(Candidate c, PlacementDecision decision, Long from, Long to, Integer step,
            ReasonCode reason, ReasonCode cause) {
        return new PlacementLog(c.applicationId(), decision, from, to, step, reason, cause, reason.text());
    }
}
