package com.siktak.venue.domain.assign;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class VenueAssignerTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 20);

    private static TableToAssign table(long id, int size, String... prices) {
        return new TableToAssign(id, size, Arrays.asList(prices));
    }

    @Test
    void 시드_S1_오크테이블2_그린키친0이면_2개_배정_1개_VENUE_UNAVAILABLE() {
        List<VenueStock> venues = List.of(
                new VenueStock(1, 2, 6, null, true), // 성수 오크테이블
                new VenueStock(2, 0, 6, null, true)); // 성수 그린키친 (S1 수용 0)
        VenueAssignmentResult r = VenueAssigner.assign(D,
                List.of(table(101, 5), table(102, 5), table(103, 4)), venues);

        assertThat(r.assigned()).extracting(VenueAssignment::tableId).containsExactly(101L, 102L);
        assertThat(r.assigned()).allMatch(a -> a.venueId() == 1);
        assertThat(r.unassigned()).containsExactly(
                new UnassignedTable(103, UnassignedReason.VENUE_UNAVAILABLE, "우연한식탁-1020-3"));
        assertThat(r.remainingTables()).containsEntry(1L, 0).containsEntry(2L, 0);
    }

    @Test
    void 예약명은_회차일_MMdd와_tableId순_순번() {
        VenueAssignmentResult r = VenueAssigner.assign(D,
                List.of(table(20, 4), table(10, 4)), List.of(new VenueStock(1, 5, 6, null, true)));

        assertThat(r.assigned()).extracting(VenueAssignment::reservationName)
                .containsExactly("우연한식탁-1020-1", "우연한식탁-1020-2");
        assertThat(r.assigned().get(0).tableId()).isEqualTo(10L);
    }

    @Test
    void 희망_가격대_일치_인원이_많은_식당_우선() {
        List<VenueStock> venues = List.of(
                new VenueStock(1, 5, 6, "UNDER_30K", true),
                new VenueStock(2, 1, 6, "30K_50K", true));
        VenueAssignmentResult r = VenueAssigner.assign(D,
                List.of(table(1, 4, "30K_50K", "30K_50K", "UNDER_30K", null)), venues);

        assertThat(r.assigned()).singleElement().satisfies(a -> {
            assertThat(a.venueId()).isEqualTo(2L);
            assertThat(a.priceMatchedCount()).isEqualTo(2);
        });
    }

    @Test
    void 가격대가_같으면_남은_테이블_많은_식당_그다음_venueId() {
        List<VenueStock> venues = List.of(
                new VenueStock(3, 1, 6, null, true),
                new VenueStock(2, 2, 6, null, true),
                new VenueStock(1, 2, 6, null, true));
        VenueAssignmentResult r = VenueAssigner.assign(D, List.of(table(1, 4), table(2, 4), table(3, 4)), venues);

        // 1번: 남은 2인 식당 1·2 중 venueId 작은 1 → 2번: 2(남은 2) → 3번: 1·2·3 모두 1 → venueId 1
        assertThat(r.assigned()).extracting(VenueAssignment::venueId).containsExactly(1L, 2L, 1L);
    }

    @Test
    void 테이블당_최대인원보다_큰_테이블은_그_식당에_배정하지_않음() {
        List<VenueStock> venues = List.of(
                new VenueStock(1, 3, 4, null, true),
                new VenueStock(2, 1, 6, null, true));
        VenueAssignmentResult r = VenueAssigner.assign(D, List.of(table(1, 6), table(2, 5)), venues);

        assertThat(r.assigned()).extracting(VenueAssignment::venueId).containsExactly(2L);
        assertThat(r.unassigned()).extracting(UnassignedTable::tableId).containsExactly(2L);
    }

    @Test
    void 비활성_식당은_제외() {
        VenueAssignmentResult r = VenueAssigner.assign(D, List.of(table(1, 4)),
                List.of(new VenueStock(1, 5, 6, null, false)));

        assertThat(r.assigned()).isEmpty();
        assertThat(r.unassigned()).extracting(UnassignedTable::reason).containsExactly(UnassignedReason.VENUE_UNAVAILABLE);
    }

    @Test
    void 테이블이_없으면_빈_결과() {
        VenueAssignmentResult r = VenueAssigner.assign(D, List.of(), List.of(new VenueStock(1, 5, 6, null, true)));

        assertThat(r.assigned()).isEmpty();
        assertThat(r.unassigned()).isEmpty();
        assertThat(r.remainingTables()).containsEntry(1L, 5);
    }
}
