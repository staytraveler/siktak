package com.siktak.matching.domain.engine;

import static com.siktak.matching.domain.engine.constraint.HardConstraints.TABLE_MAX;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

import com.siktak.matching.domain.engine.constraint.HardConstraints;
import com.siktak.matching.domain.engine.score.TableScorer;

/**
 * 확정 후 취소로 빈 자리에 들어갈 대기자 고르기 (STA-07, PRD 15장 "조건에 맞는 대기자를 자동 충원"). 순수 함수.
 * 필수 조건(최대 인원·연령 차·차단·피하고 싶음)과 최소 점수를 그대로 지키고, 조건을 통과한 대기자 중
 * 테이블 점수가 가장 높아지는 사람 한 명을 고른다 (동점이면 신청 ID가 작은 사람).
 */
final class WaitlistFill {

    private static final double EPS = 1e-9;

    private WaitlistFill() {
    }

    static OptionalLong pick(List<Candidate> members, List<Candidate> waiters, Set<UserPair> blocked,
            Set<UserPair> encounters, Set<UserPair> wantAgain, MatchingConfig config) {
        if (members.isEmpty() || members.size() >= TABLE_MAX) {
            return OptionalLong.empty();
        }
        Candidate best = null;
        double bestScore = -1;
        List<Candidate> sorted = new ArrayList<>(waiters);
        sorted.sort(Comparator.comparingLong(Candidate::applicationId));
        for (Candidate w : sorted) {
            if (!w.paid() || !w.accountActive() || members.stream().anyMatch(m -> m.userId() == w.userId())) {
                continue;
            }
            List<Candidate> next = new ArrayList<>(members);
            next.add(w);
            if (next.size() >= 2 && !HardConstraints.compatible(next, blocked)) {
                continue;
            }
            double score = TableScorer.total(next, encounters, wantAgain, config.weights());
            if (score < config.minScore() - EPS) {
                continue;
            }
            if (best == null || score > bestScore + EPS) {
                best = w;
                bestScore = score;
            }
        }
        return best == null ? OptionalLong.empty() : OptionalLong.of(best.applicationId());
    }
}
