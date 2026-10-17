package com.siktak.matching.domain.alternative;

import static com.siktak.matching.domain.alternative.AlternativeSlot.Reason.CHOSEN_REGION;
import static com.siktak.matching.domain.alternative.AlternativeSlot.Reason.OTHER_REGION;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

class AlternativeSlotRecommenderTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate D = LocalDate.of(2026, 10, 20);
    private static final long SEONGSU = 1;
    private static final long HONGDAE = 2;
    private static final long GANGNAM = 3;

    private static Instant at(LocalDate date, int hour) {
        return date.atTime(LocalTime.of(hour, 0)).atZone(SEOUL).toInstant();
    }

    /** demo-scenario.md 2장 회차. 매칭 실행 = 행사 2일 전 12:00 (S3만 1일 전). */
    private static List<SlotOption> demoSlots(boolean s1s2Matched) {
        return List.of(
                new SlotOption(1, D, SEONGSU, !s1s2Matched, at(D.minusDays(2), 12)),
                new SlotOption(2, D, HONGDAE, !s1s2Matched, at(D.minusDays(2), 12)),
                new SlotOption(3, D.plusDays(1), SEONGSU, true, at(D.minusDays(1), 12)),
                new SlotOption(4, D.plusDays(7), SEONGSU, true, at(D.plusDays(5), 12)),
                new SlotOption(5, D.plusDays(7), GANGNAM, true, at(D.plusDays(5), 12)));
    }

    @Test
    void J2_user14_성수_선택자에게_S4_성수_다음_S5_강남() {
        // J2: 시계 = S3 매칭 실행 시각. user14는 S1 → S3를 선택했다.
        List<AlternativeSlot> r = AlternativeSlotRecommender.recommend(List.of(1L, 3L), demoSlots(true),
                at(D.minusDays(1), 12), 3);

        assertThat(r).containsExactly(new AlternativeSlot(4, CHOSEN_REGION), new AlternativeSlot(5, OTHER_REGION));
        assertThat(r.get(0).reason().text()).isEqualTo("선택하신 지역");
    }

    @Test
    void 매칭시각이_지난_회차는_제외() {
        // S1·S2가 아직 OPEN이어도 매칭 시각이 지났으면 추천하지 않는다.
        List<AlternativeSlot> r = AlternativeSlotRecommender.recommend(List.of(3L), demoSlots(false),
                at(D.minusDays(1), 12), 3);

        assertThat(r).extracting(AlternativeSlot::slotId).containsExactly(4L, 5L);
    }

    @Test
    void 모집중이_아닌_회차는_제외() {
        List<SlotOption> slots = List.of(
                new SlotOption(4, D.plusDays(7), SEONGSU, false, at(D.plusDays(5), 12)),
                new SlotOption(5, D.plusDays(7), GANGNAM, true, at(D.plusDays(5), 12)));
        List<AlternativeSlot> r = AlternativeSlotRecommender.recommend(List.of(), slots, at(D, 0), 3);

        assertThat(r).extracting(AlternativeSlot::slotId).containsExactly(5L);
    }

    @Test
    void 이미_선택한_회차는_제외() {
        List<AlternativeSlot> r = AlternativeSlotRecommender.recommend(List.of(3L, 4L), demoSlots(true),
                at(D.minusDays(1), 11), 3);

        assertThat(r).extracting(AlternativeSlot::slotId).containsExactly(5L);
    }

    @Test
    void 선택_지역_안에서는_날짜_빠른순_최대_3개() {
        List<SlotOption> slots = List.of(
                new SlotOption(10, D.plusDays(9), SEONGSU, true, at(D.plusDays(7), 12)),
                new SlotOption(11, D.plusDays(8), SEONGSU, true, at(D.plusDays(6), 12)),
                new SlotOption(12, D.plusDays(2), HONGDAE, true, at(D, 12)),
                new SlotOption(13, D.plusDays(8), SEONGSU, true, at(D.plusDays(6), 12)),
                new SlotOption(1, D, SEONGSU, false, at(D.minusDays(2), 12)));
        List<AlternativeSlot> r = AlternativeSlotRecommender.recommend(List.of(1L), slots, at(D.minusDays(1), 12), 3);

        // 성수(선택 지역) 11·13(같은 날짜 → slotId) → 10, 홍대 12는 4번째라 잘림
        assertThat(r).extracting(AlternativeSlot::slotId).containsExactly(11L, 13L, 10L);
        assertThat(r).extracting(AlternativeSlot::reason).containsOnly(CHOSEN_REGION);
    }

    @Test
    void 추천할_회차가_없으면_빈_목록() {
        List<AlternativeSlot> r = AlternativeSlotRecommender.recommend(List.of(1L), demoSlots(true),
                at(D.plusDays(6), 0), 3);

        assertThat(r).isEmpty();
    }
}
