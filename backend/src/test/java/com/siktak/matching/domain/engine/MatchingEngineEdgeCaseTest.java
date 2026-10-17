package com.siktak.matching.domain.engine;

import static com.siktak.matching.domain.engine.Fixtures.D;
import static com.siktak.matching.domain.engine.Fixtures.alternating;
import static com.siktak.matching.domain.engine.Fixtures.cand;
import static com.siktak.matching.domain.engine.Fixtures.ids;
import static com.siktak.matching.domain.engine.Fixtures.input;
import static com.siktak.matching.domain.engine.Fixtures.lastLog;
import static com.siktak.matching.domain.engine.Fixtures.logsOf;
import static com.siktak.matching.domain.engine.Fixtures.run;
import static com.siktak.matching.domain.engine.Fixtures.sizesDesc;
import static com.siktak.matching.domain.engine.Fixtures.slot;
import static com.siktak.matching.domain.engine.Fixtures.tableOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.siktak.matching.domain.engine.score.ScoreCode;
import com.siktak.profile.domain.Interest;
import com.siktak.profile.domain.Mbti;
import com.siktak.profile.domain.Trait;

/** matching.md 8장 엣지 케이스 (E20~E22는 4.4·6.x 범위). */
class MatchingEngineEdgeCaseTest {

    private static final List<SlotInfo> ONE_SLOT = List.of(slot(1, D, 1));

    /** 남 1~4, 여 5~8, 같은 출생연도. 초기 배정은 T0 {1,3,5,7}, T1 {2,4,6,8}. */
    private static List<Candidate> eightMixed() {
        List<Candidate> list = new ArrayList<>();
        for (long id = 1; id <= 4; id++) {
            list.add(cand(id).male().build());
        }
        for (long id = 5; id <= 8; id++) {
            list.add(cand(id).female().build());
        }
        return list;
    }

    @Test
    void E1_후보4명_4인테이블_1개_확정() {
        MatchingResult r = run(input(ONE_SLOT, alternating(1, 4)));

        assertThat(r.tables()).hasSize(1);
        assertThat(r.tables().get(0).members()).containsExactly(1L, 2L, 3L, 4L);
        assertThat(r.failed()).isEmpty();
        for (long id = 1; id <= 4; id++) {
            assertThat(lastLog(r, id).decision()).isEqualTo(PlacementDecision.CONFIRMED);
            assertThat(lastLog(r, id).reasonCode()).isEqualTo(ReasonCode.PLACED_FIRST_CHOICE);
        }
    }

    @Test
    void E2_후보6명_6인테이블_1개() {
        MatchingResult r = run(input(ONE_SLOT, alternating(1, 6)));

        assertThat(sizesDesc(r)).containsExactly(6);
        assertThat(r.summary().confirmedMembers()).isEqualTo(6);
    }

    @Test
    void E3_후보7명_6인확정_1명_대체일정() {
        // 남 1~4, 여 5~7: 남자 1명을 빼야 3:3. 나머지 조건이 같으면 나중 신청(4)을 뺀다.
        List<Candidate> cands = new ArrayList<>();
        for (long id = 1; id <= 4; id++) {
            cands.add(cand(id).male().build());
        }
        for (long id = 5; id <= 7; id++) {
            cands.add(cand(id).female().build());
        }
        MatchingResult r = run(input(ONE_SLOT, cands));

        assertThat(r.tables()).hasSize(1);
        assertThat(r.tables().get(0).members()).containsExactly(1L, 2L, 3L, 5L, 6L, 7L);
        assertThat(ids(r.failed())).containsExactly(4L);
        assertThat(r.failed().get(0).reasonCode()).isEqualTo(ReasonCode.UNDER_MIN_SIZE);
        assertThat(r.deferred()).isEmpty();
        assertThat(lastLog(r, 4).decision()).isEqualTo(PlacementDecision.FAILED);
    }

    @Test
    void E4_7명중_1명이_같은날_다른지역_선택_2단계_재배치로_전원확정() {
        List<SlotInfo> slots = List.of(slot(1, D, 1), slot(2, D, 2));
        List<Candidate> cands = new ArrayList<>();
        for (long id = 1; id <= 3; id++) {
            cands.add(cand(id).male().born(1995).build());
        }
        for (long id = 4; id <= 6; id++) {
            cands.add(cand(id).female().born(1995).build());
        }
        // 7번은 나이 차가 커서 1회차 6인 조합에서 빠지고, 같은 날 다른 지역(2회차)도 선택했다.
        cands.add(cand(7).male().born(2001).slots(1L, 2L).build());
        cands.add(cand(11).male().born(1999).slots(2L).build());
        cands.add(cand(12).male().born(1999).slots(2L).build());
        cands.add(cand(13).female().born(1999).slots(2L).build());
        cands.add(cand(14).female().born(1999).slots(2L).build());

        MatchingResult r = run(input(slots, cands));

        assertThat(tableOf(r, 1).members()).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(tableOf(r, 7).slotId()).isEqualTo(2L);
        assertThat(tableOf(r, 7).members()).containsExactly(7L, 11L, 12L, 13L, 14L);
        assertThat(r.failed()).isEmpty();

        PlacementLog moved = logsOf(r, 7).stream()
                .filter(l -> l.decision() == PlacementDecision.REALLOCATED).findFirst().orElseThrow();
        assertThat(moved.reallocStep()).isEqualTo(2);
        assertThat(moved.fromSlotId()).isEqualTo(1L);
        assertThat(moved.toSlotId()).isEqualTo(2L);
        assertThat(moved.reasonCode()).isEqualTo(ReasonCode.REALLOCATED_OTHER_REGION);
        assertThat(lastLog(r, 7).decision()).isEqualTo(PlacementDecision.CONFIRMED);
        assertThat(lastLog(r, 7).reasonCode()).isEqualTo(ReasonCode.REALLOCATED_OTHER_REGION);
    }

    @Test
    void E5_후보8명_4인_2테이블() {
        MatchingResult r = run(input(ONE_SLOT, alternating(1, 8)));

        assertThat(sizesDesc(r)).containsExactly(4, 4);
        assertThat(r.summary().splitGroups()).isEqualTo(1);
    }

    @Test
    void E6_후보13명_5_4_4_세테이블() {
        MatchingResult r = run(input(ONE_SLOT, alternating(1, 13)));

        assertThat(sizesDesc(r)).containsExactly(5, 4, 4);
        assertThat(r.failed()).isEmpty();
    }

    @Test
    void E7_후보3명_전원_미배치_UNDER_MIN_SIZE() {
        MatchingResult r = run(input(ONE_SLOT, alternating(1, 3)));

        assertThat(r.tables()).isEmpty();
        assertThat(ids(r.failed())).containsExactly(1L, 2L, 3L);
        assertThat(r.failed()).allMatch(u -> u.reasonCode() == ReasonCode.UNDER_MIN_SIZE);
    }

    @Test
    void E8_5명중_1명만_나이차_9살이상_4인확정_1명_AGE_GAP() {
        List<Candidate> cands = List.of(
                cand(1).male().born(1995).build(),
                cand(2).female().born(1996).build(),
                cand(3).male().born(1996).build(),
                cand(4).female().born(1995).build(),
                cand(5).female().born(2005).build());
        MatchingResult r = run(input(ONE_SLOT, cands));

        assertThat(r.tables()).hasSize(1);
        assertThat(r.tables().get(0).members()).containsExactly(1L, 2L, 3L, 4L);
        assertThat(ids(r.failed())).containsExactly(5L);
        assertThat(r.failed().get(0).reasonCode()).isEqualTo(ReasonCode.AGE_GAP);
    }

    @Test
    void E9_연령그룹_4_6_그룹별_구성_혼합없음() {
        List<Candidate> cands = new ArrayList<>();
        int[] older = {1980, 1981, 1982, 1982};
        for (int i = 0; i < older.length; i++) {
            Fixtures.Cand c = cand(i + 1).born(older[i]);
            cands.add((i % 2 == 0 ? c.male() : c.female()).build());
        }
        int[] younger = {1995, 1996, 1996, 1997, 1998, 1998};
        for (int i = 0; i < younger.length; i++) {
            Fixtures.Cand c = cand(i + 11).born(younger[i]);
            cands.add((i % 2 == 0 ? c.male() : c.female()).build());
        }
        MatchingResult r = run(input(ONE_SLOT, cands));

        assertThat(r.tables()).hasSize(2);
        assertThat(tableOf(r, 1).members()).containsExactly(1L, 2L, 3L, 4L);
        assertThat(tableOf(r, 11).members()).containsExactly(11L, 12L, 13L, 14L, 15L, 16L);
    }

    @Test
    void E10_5명중_2명_차단_다른테이블없음_1명_BLOCKED_PAIR() {
        MatchingResult r = run(input(ONE_SLOT, alternating(1, 5), Set.of(UserPair.of(2, 5)), Set.of()));

        assertThat(r.tables()).hasSize(1);
        assertThat(r.tables().get(0).members()).containsExactly(1L, 2L, 3L, 4L);
        assertThat(ids(r.failed())).containsExactly(5L);
        assertThat(r.failed().get(0).reasonCode()).isEqualTo(ReasonCode.BLOCKED_PAIR);
        assertThat(lastLog(r, 5).reasonText()).doesNotContain("차단");
    }

    @Test
    void E11_8명중_2명_차단_교환으로_분리_전원확정() {
        MatchingResult r = run(input(ONE_SLOT, eightMixed(), Set.of(UserPair.of(1, 3)), Set.of()));

        assertThat(sizesDesc(r)).containsExactly(4, 4);
        assertThat(r.summary().confirmedMembers()).isEqualTo(8);
        assertThat(tableOf(r, 1)).isNotSameAs(tableOf(r, 3));
        assertThat(tableOf(r, 1).members()).doesNotContain(3L);
    }

    @Test
    void E12_AVOID_단방향_선택도_차단과_동일하게_분리() {
        // 7번만 5번을 "다시 만나고 싶지 않음"으로 선택했다. 관계는 쌍 하나로 들어온다.
        MatchingResult r = run(input(ONE_SLOT, eightMixed(), Set.of(UserPair.of(7, 5)), Set.of()));

        assertThat(r.summary().confirmedMembers()).isEqualTo(8);
        assertThat(tableOf(r, 5).members()).doesNotContain(7L);
    }

    @Test
    void E13_이전에_만난_쌍은_가능하면_다른_테이블() {
        Set<UserPair> met = Set.of(UserPair.of(1, 3), UserPair.of(5, 7));
        MatchingResult r = run(input(ONE_SLOT, eightMixed(), Set.of(), met));

        assertThat(r.summary().confirmedMembers()).isEqualTo(8);
        assertThat(tableOf(r, 1).members()).doesNotContain(3L);
        assertThat(tableOf(r, 5).members()).doesNotContain(7L);
        assertThat(r.tables()).allSatisfy(t -> {
            assertThat(t.summary().previousEncounters()).isEmpty();
            assertThat(t.scoreDetail().item(ScoreCode.S6).raw()).isEqualTo(1.0);
        });
    }

    @Test
    void E14_전원남성_6명_S2는_0_최소점수_미만이면_해체() {
        Interest[] distinct = {Interest.FOOD, Interest.TRAVEL, Interest.MOVIE, Interest.SPORTS, Interest.READING,
                Interest.EXHIBITION};
        List<Candidate> cands = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            cands.add(cand(i + 1).male().mbti(Mbti.ISTJ).interests(distinct[i])
                    .self(Trait.LISTENER).prefers(Trait.HUMOROUS).build());
        }
        // S1 20 + S2 0 + S3 0 + S4 0 + S5 0 + S6 12 = 32 < 55
        MatchingResult r = run(input(ONE_SLOT, cands));

        assertThat(r.tables()).isEmpty();
        assertThat(ids(r.failed())).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(r.failed()).allMatch(u -> u.reasonCode() == ReasonCode.LOW_SCORE);
        assertThat(r.summary().dissolvedTables()).isEqualTo(1);
    }

    @Test
    void E14_전원남성_6명_다른항목이_좋으면_필수조건_통과로_확정() {
        List<Candidate> cands = new ArrayList<>();
        for (long id = 1; id <= 6; id++) {
            cands.add(cand(id).male().build());
        }
        MatchingResult r = run(input(ONE_SLOT, cands));

        assertThat(r.tables()).hasSize(1);
        assertThat(r.tables().get(0).scoreDetail().item(ScoreCode.S2).raw()).isEqualTo(0.0);
        assertThat(r.tables().get(0).totalScore()).isCloseTo(71.0, within(1e-9));
    }

    @Test
    void E15_MBTI_전원_미입력_S3는_0점5() {
        MatchingResult r = run(input(ONE_SLOT, alternating(1, 4)));

        assertThat(r.tables()).hasSize(1);
        assertThat(r.tables().get(0).scoreDetail().item(ScoreCode.S3).raw()).isEqualTo(0.5);
        assertThat(r.tables().get(0).summary().mbti().unknown()).isEqualTo(4);
    }

    @Test
    void E16_두회차_선택_1순위에서_확정되면_2순위에_중복배치_없음() {
        List<SlotInfo> slots = List.of(slot(1, D, 1), slot(2, D.plusDays(1), 1));
        List<Candidate> cands = new ArrayList<>();
        cands.add(cand(1).male().slots(1L, 2L).build());
        cands.add(cand(2).female().build());
        cands.add(cand(3).male().build());
        cands.add(cand(4).female().build());
        for (long id = 11; id <= 14; id++) {
            cands.add((id % 2 == 1 ? cand(id).male() : cand(id).female()).slots(2L).build());
        }
        MatchingResult r = run(input(slots, cands));

        assertThat(tableOf(r, 1).slotId()).isEqualTo(1L);
        assertThat(r.tables().stream().filter(t -> t.members().contains(1L))).hasSize(1);
        assertThat(tableOf(r, 11).members()).containsExactly(11L, 12L, 13L, 14L);
    }

    @Test
    void E16_같은사용자의_두신청이_같은날짜에_배치되면_하나만_확정_H7() {
        // 사용자 100이 같은 날 1회차(신청 1)와 2회차(신청 11)에 각각 신청했다.
        List<SlotInfo> slots = List.of(slot(1, D, 1), slot(2, D, 2));
        List<Candidate> cands = new ArrayList<>(alternating(2, 5));
        cands.add(cand(1).user(100).female().build());
        for (long id = 12; id <= 15; id++) {
            cands.add((id % 2 == 1 ? cand(id).male() : cand(id).female()).slots(2L).build());
        }
        cands.add(cand(11).user(100).female().slots(2L).build());
        MatchingResult r = run(input(slots, cands));

        assertThat(tableOf(r, 1)).isNotNull();
        assertThat(tableOf(r, 11)).isNull();
        assertThat(ids(r.failed())).containsExactly(11L);
        assertThat(r.failed().get(0).reasonCode()).isEqualTo(ReasonCode.DUPLICATE_DATE);
        assertThat(tableOf(r, 12).members()).containsExactly(12L, 13L, 14L, 15L);
    }

    @Test
    void E17_1순위_실패_2순위_매칭시각_미도래면_WAITING_복귀() {
        // 회차 99는 이번 실행 대상이 아니다 (매칭 시각 미도래).
        List<Candidate> cands = List.of(
                cand(1).male().slots(1L, 99L).build(),
                cand(2).female().build(),
                cand(3).male().build());
        MatchingResult r = run(input(ONE_SLOT, cands));

        assertThat(ids(r.deferred())).containsExactly(1L);
        assertThat(r.deferred().get(0).reasonCode()).isEqualTo(ReasonCode.DEFERRED);
        assertThat(r.deferred().get(0).causeCode()).isEqualTo(ReasonCode.UNDER_MIN_SIZE);
        assertThat(lastLog(r, 1).decision()).isEqualTo(PlacementDecision.DEFERRED);
        assertThat(ids(r.failed())).containsExactly(2L, 3L);
    }

    @Test
    void E18_결제_PENDING_신청은_후보에서_제외() {
        List<Candidate> cands = new ArrayList<>(alternating(1, 4));
        cands.add(cand(5).female().unpaid().build());
        MatchingResult r = run(input(ONE_SLOT, cands));

        assertThat(r.excluded()).containsExactly(new ExcludedCandidate(5, 5, ReasonCode.NOT_PAID));
        assertThat(r.tables().get(0).members()).containsExactly(1L, 2L, 3L, 4L);
        assertThat(logsOf(r, 5)).singleElement()
                .extracting(PlacementLog::decision).isEqualTo(PlacementDecision.EXCLUDED);
    }

    @Test
    void E19_참가제한_사용자는_후보에서_제외_예외기록용_사유() {
        List<Candidate> cands = new ArrayList<>(alternating(1, 4));
        cands.add(cand(5).female().restricted().build());
        MatchingResult r = run(input(ONE_SLOT, cands));

        assertThat(r.excluded()).containsExactly(new ExcludedCandidate(5, 5, ReasonCode.ACCOUNT_RESTRICTED));
        assertThat(tableOf(r, 5)).isNull();
        assertThat(r.summary().excludedCount()).isEqualTo(1);
    }

    @Test
    void E23_동일입력_두번_계산하면_결과_완전동일() {
        MatchingInput in = DemoSeed.j1Input();
        assertThat(new MatchingEngine().run(in)).isEqualTo(new MatchingEngine().run(in));
    }

    @Test
    void E23_후보_입력순서가_달라도_결과_동일() {
        MatchingInput in = DemoSeed.j1Input();
        List<Candidate> reversed = new ArrayList<>(in.candidates());
        java.util.Collections.reverse(reversed);
        MatchingInput shuffled = new MatchingInput(in.slots(), reversed, in.blockedPairs(), in.encounters(),
                in.config());
        assertThat(new MatchingEngine().run(shuffled)).isEqualTo(new MatchingEngine().run(in));
    }

    @Test
    void E24_후보0명_빈결과_오류없음() {
        MatchingResult r = run(input(ONE_SLOT, List.of()));

        assertThat(r.tables()).isEmpty();
        assertThat(r.placementLogs()).isEmpty();
        assertThat(r.deferred()).isEmpty();
        assertThat(r.failed()).isEmpty();
        assertThat(r.excluded()).isEmpty();
        assertThat(r.summary().candidateCount()).isZero();
    }
}
