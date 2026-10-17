package com.siktak.matching.domain.engine;

import static com.siktak.matching.domain.engine.Fixtures.D;
import static com.siktak.matching.domain.engine.Fixtures.alternating;
import static com.siktak.matching.domain.engine.Fixtures.cand;
import static com.siktak.matching.domain.engine.Fixtures.slot;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.siktak.matching.domain.engine.CancelReallocationInput.OpenTable;
import com.siktak.matching.domain.engine.CancelReallocationResult.Move;
import com.siktak.matching.domain.engine.CancelReallocationResult.Outcome;
import com.siktak.matching.domain.engine.score.TableScorer;
import com.siktak.profile.domain.Interest;
import com.siktak.profile.domain.Mbti;
import com.siktak.profile.domain.Trait;

/** matching.md 6장 확정 후 취소 재배치 (E21·E22). */
class CancelReallocationTest {

    private static final SlotInfo SLOT = slot(1, D, 1);
    private static final MatchingEngine ENGINE = new MatchingEngine();

    private static CancelReallocationResult run(List<Candidate> remaining, List<OpenTable> open, Set<UserPair> blocked) {
        return ENGINE.reallocateAfterCancel(new CancelReallocationInput(SLOT, 100, remaining, open, blocked, Set.of(),
                MatchingConfig.defaults()));
    }

    @Test
    void E21_5명중_2명_취소로_3명_남으면_자리_남은_확정테이블로_재배치_자리없으면_대체일정() {
        // 원래 테이블 100: 1~5 중 4·5 취소 → 1·2·3 남음. 테이블 200은 4명, 300은 6명(만석).
        List<OpenTable> open = List.of(
                new OpenTable(200, 1, alternating(11, 14)),
                new OpenTable(300, 1, alternating(21, 26)));
        CancelReallocationResult r = run(alternating(1, 3), open, Set.of());

        assertThat(r.outcome()).isEqualTo(Outcome.DISSOLVED);
        assertThat(r.keptTable()).isNull();
        assertThat(r.moves()).containsExactly(new Move(1, 200), new Move(2, 200));
        assertThat(r.failed()).extracting(UnplacedCandidate::applicationId).containsExactly(3L);
        assertThat(r.failed().get(0).reasonCode()).isEqualTo(ReasonCode.UNDER_MIN_SIZE);
        assertThat(r.updatedTables()).singleElement().satisfies(t -> {
            assertThat(t.tableId()).isEqualTo(200L);
            assertThat(t.members()).containsExactly(1L, 2L, 11L, 12L, 13L, 14L);
            assertThat(t.scoreDetail().total()).isGreaterThanOrEqualTo(MatchingConfig.defaults().minScore());
        });
        assertThat(r.placementLogs()).filteredOn(l -> l.applicationId() == 1)
                .extracting(PlacementLog::decision)
                .containsExactly(PlacementDecision.REALLOCATED, PlacementDecision.CONFIRMED);
        assertThat(r.placementLogs()).filteredOn(l -> l.applicationId() == 1)
                .allMatch(l -> l.reasonCode() == ReasonCode.REALLOCATED_SAME_SLOT && l.reallocStep() == 1);
    }

    @Test
    void E21_들어갈_확정테이블이_없으면_3명_모두_대체일정() {
        CancelReallocationResult r = run(alternating(1, 3), List.of(), Set.of());

        assertThat(r.outcome()).isEqualTo(Outcome.DISSOLVED);
        assertThat(r.moves()).isEmpty();
        assertThat(r.failed()).extracting(UnplacedCandidate::applicationId).containsExactly(1L, 2L, 3L);
        assertThat(r.placementLogs()).allMatch(l -> l.decision() == PlacementDecision.FAILED);
    }

    @Test
    void E21_재배치도_필수조건_유지_차단관계_테이블과_다른_회차_테이블은_제외() {
        List<OpenTable> open = List.of(
                new OpenTable(200, 1, alternating(11, 14)),
                new OpenTable(400, 1, alternating(31, 35)),
                new OpenTable(500, 2, alternating(41, 44)), // 다른 회차
                new OpenTable(100, 1, alternating(51, 54))); // 원래 테이블 자신
        // 1번은 11번과 차단 관계라 200에 못 들어간다.
        CancelReallocationResult r = run(alternating(1, 2), open, Set.of(UserPair.of(1, 11)));

        assertThat(r.moves()).contains(new Move(1, 400));
        assertThat(r.moves()).extracting(Move::toTableId).doesNotContain(500L, 100L);
        assertThat(r.failed()).isEmpty();
    }

    @Test
    void E21_넣은_뒤_최소점수_미만이면_넣지_않는다() {
        // 남자 5명 테이블에 남자를 넣으면 6:0이 되고, MBTI·관심사도 겹치지 않게 만들어 최소 점수 미만이 되게 한다.
        List<Candidate> men = List.of(
                cand(11).male().interests(Interest.TRAVEL)
                        .prefers(Trait.HUMOROUS).mbti(Mbti.ISTJ).build(),
                cand(12).male().interests(Interest.MOVIE)
                        .prefers(Trait.HUMOROUS).mbti(Mbti.ISTJ).build(),
                cand(13).male().interests(Interest.GAME)
                        .prefers(Trait.HUMOROUS).mbti(Mbti.ISTJ).build(),
                cand(14).male().interests(Interest.PET)
                        .prefers(Trait.HUMOROUS).mbti(Mbti.ISTJ).build(),
                cand(15).male().interests(Interest.MUSIC)
                        .prefers(Trait.HUMOROUS).mbti(Mbti.ISTJ).build());
        Candidate newcomer = cand(1).male().interests(Interest.CAREER)
                .prefers(Trait.HUMOROUS).mbti(Mbti.ISTJ).build();
        assertThat(TableScorer.total(List.of(newcomer, men.get(0), men.get(1), men.get(2), men.get(3), men.get(4)),
                Set.of(), MatchingConfig.defaults().weights())).isLessThan(55);

        CancelReallocationResult r = run(List.of(newcomer), List.of(new OpenTable(200, 1, men)), Set.of());

        assertThat(r.moves()).isEmpty();
        assertThat(r.failed()).extracting(UnplacedCandidate::applicationId).containsExactly(1L);
    }

    @Test
    void E22_6명중_1명_취소로_5명이면_테이블_유지_점수만_재계산() {
        List<Candidate> five = alternating(1, 5);
        CancelReallocationResult r = run(five,
                List.of(new OpenTable(200, 1, alternating(11, 14))), Set.of());

        assertThat(r.outcome()).isEqualTo(Outcome.KEPT);
        assertThat(r.keptTable().tableId()).isEqualTo(100L);
        assertThat(r.keptTable().members()).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(r.keptTable().scoreDetail().total())
                .isEqualTo(TableScorer.total(five, Set.of(), MatchingConfig.defaults().weights()));
        assertThat(r.keptTable().summary().memberCount()).isEqualTo(5);
        assertThat(r.moves()).isEmpty();
        assertThat(r.failed()).isEmpty();
        assertThat(r.updatedTables()).isEmpty();
    }

    @Test
    void 같은_입력이면_같은_결과() {
        List<OpenTable> open = List.of(new OpenTable(300, 1, alternating(21, 24)), new OpenTable(200, 1, alternating(11, 14)));
        assertThat(run(alternating(1, 3), open, Set.of())).isEqualTo(run(alternating(1, 3), open, Set.of()));
    }
}
