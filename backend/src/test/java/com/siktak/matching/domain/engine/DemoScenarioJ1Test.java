package com.siktak.matching.domain.engine;

import static com.siktak.matching.domain.engine.DemoSeed.S1;
import static com.siktak.matching.domain.engine.DemoSeed.S2;
import static com.siktak.matching.domain.engine.Fixtures.ids;
import static com.siktak.matching.domain.engine.Fixtures.lastLog;
import static com.siktak.matching.domain.engine.Fixtures.logsOf;
import static com.siktak.matching.domain.engine.Fixtures.run;
import static com.siktak.matching.domain.engine.Fixtures.tableOf;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** docs/demo-scenario.md 4장 "J1 후" 기대 결과 (식당 배정 제외). */
class DemoScenarioJ1Test {

    private static MatchingResult result;

    @BeforeAll
    static void runJ1() {
        result = run(DemoSeed.j1Input());
    }

    private static List<ProposedTable> tablesIn(long slotId) {
        return result.tables().stream().filter(t -> t.slotId() == slotId).toList();
    }

    @Test
    void S2_연령그룹_1990_1993_6명이_6인테이블로_확정() {
        assertThat(tablesIn(S2)).singleElement()
                .satisfies(t -> assertThat(t.members()).containsExactly(17L, 18L, 19L, 20L, 21L, 22L));
    }

    @Test
    void user15_S2_AGE_GAP_후_같은날_다른지역_S1로_2단계_재배치() {
        PlacementLog moved = logsOf(result, 15).stream()
                .filter(l -> l.decision() == PlacementDecision.REALLOCATED).findFirst().orElseThrow();
        assertThat(moved.fromSlotId()).isEqualTo(S2);
        assertThat(moved.toSlotId()).isEqualTo(S1);
        assertThat(moved.reallocStep()).isEqualTo(2);
        assertThat(moved.reasonCode()).isEqualTo(ReasonCode.REALLOCATED_OTHER_REGION);
        assertThat(moved.causeCode()).isEqualTo(ReasonCode.AGE_GAP);

        assertThat(tableOf(result, 15).slotId()).isEqualTo(S1);
        assertThat(lastLog(result, 15).decision()).isEqualTo(PlacementDecision.CONFIRMED);
    }

    @Test
    void user15는_S1의_4인테이블로_이동() {
        assertThat(tableOf(result, 15).members()).hasSize(5);
    }

    @Test
    void user14_S1_AGE_GAP_재배치불가_S3_미도래로_WAITING_복귀() {
        assertThat(ids(result.deferred())).containsExactly(14L);
        assertThat(result.deferred().get(0).reasonCode()).isEqualTo(ReasonCode.DEFERRED);
        assertThat(result.deferred().get(0).causeCode()).isEqualTo(ReasonCode.AGE_GAP);
        assertThat(lastLog(result, 14).decision()).isEqualTo(PlacementDecision.DEFERRED);
    }

    @Test
    void user16_미결제로_후보_제외() {
        assertThat(result.excluded()).containsExactly(new ExcludedCandidate(16, 16, ReasonCode.NOT_PAID));
    }

    @Test
    void S1_3개_테이블로_분리_user01_13과_user15_전원_확정() {
        assertThat(tablesIn(S1)).hasSize(3);
        List<Long> placed = tablesIn(S1).stream().flatMap(t -> t.members().stream()).sorted().toList();
        List<Long> expected = LongStream.concat(LongStream.rangeClosed(1, 13), LongStream.of(15)).boxed().toList();
        assertThat(placed).isEqualTo(expected);
        assertThat(result.failed()).isEmpty();
        assertThat(result.summary().splitGroups()).isEqualTo(1);
    }

    @Test
    void user05와_user06은_다른_테이블() {
        assertThat(tableOf(result, 5)).isNotEqualTo(tableOf(result, 6));
    }
}
