package com.siktak.venue.domain.assign;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 식당 자동 배정 (PRD INF-02, 4.2 지시서 1장). 순수 함수.
 *
 * <p>회차 1개의 확정 테이블을 tableId 오름차순으로 하나씩, 그 회차 지역의 식당 재고에 배정한다.
 * 후보가 없는 테이블은 {@code VENUE_UNAVAILABLE}로 돌려주고, 예외 생성은 호출 측이 한다.
 */
public final class VenueAssigner {

    private static final DateTimeFormatter MMDD = DateTimeFormatter.ofPattern("MMdd");

    private VenueAssigner() {
    }

    public static VenueAssignmentResult assign(LocalDate slotDate, List<TableToAssign> tables, List<VenueStock> venues) {
        Objects.requireNonNull(slotDate, "slotDate");
        TreeMap<Long, Integer> remaining = new TreeMap<>();
        for (VenueStock v : venues) {
            if (remaining.put(v.venueId(), v.remainingTables()) != null) {
                throw new IllegalArgumentException("식당 ID 중복: " + v.venueId());
            }
        }

        List<TableToAssign> ordered = tables.stream().sorted(Comparator.comparingLong(TableToAssign::tableId)).toList();
        List<VenueAssignment> assigned = new ArrayList<>();
        List<UnassignedTable> unassigned = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            TableToAssign table = ordered.get(i);
            String reservationName = reservationName(slotDate, i + 1);
            VenueStock best = null;
            for (VenueStock v : venues) {
                if (!eligible(v, table, remaining.get(v.venueId()))) {
                    continue;
                }
                if (best == null || better(v, best, table, remaining)) {
                    best = v;
                }
            }
            if (best == null) {
                unassigned.add(new UnassignedTable(table.tableId(), UnassignedReason.VENUE_UNAVAILABLE, reservationName));
            } else {
                remaining.merge(best.venueId(), -1, Integer::sum);
                assigned.add(new VenueAssignment(table.tableId(), best.venueId(), reservationName,
                        priceMatches(best, table)));
            }
        }
        return new VenueAssignmentResult(assigned, unassigned, remaining);
    }

    /** 예약명 제안: {@code 우연한식탁-{회차일 MMdd}-{테이블 순번}}. 순번은 회차 안 tableId 오름차순 1부터. */
    public static String reservationName(LocalDate slotDate, int sequence) {
        return "우연한식탁-" + slotDate.format(MMDD) + "-" + sequence;
    }

    private static boolean eligible(VenueStock v, TableToAssign table, int remainingTables) {
        return v.active() && remainingTables >= 1 && v.maxSeatsPerTable() >= table.memberCount();
    }

    /** 가격대 일치 인원 많은 순 → 남은 테이블 수 많은 순 → venueId 오름차순. */
    private static boolean better(VenueStock a, VenueStock b, TableToAssign table, TreeMap<Long, Integer> remaining) {
        int byPrice = Integer.compare(priceMatches(a, table), priceMatches(b, table));
        if (byPrice != 0) {
            return byPrice > 0;
        }
        int byRemaining = Integer.compare(remaining.get(a.venueId()), remaining.get(b.venueId()));
        if (byRemaining != 0) {
            return byRemaining > 0;
        }
        return a.venueId() < b.venueId();
    }

    /** 멤버 희망 가격대가 식당 가격대와 같은 인원 수. 미입력(null)은 세지 않는다. */
    static int priceMatches(VenueStock venue, TableToAssign table) {
        if (venue.priceRange() == null) {
            return 0;
        }
        int matches = 0;
        for (String preferred : table.preferredPriceRanges()) {
            if (venue.priceRange().equals(preferred)) {
                matches++;
            }
        }
        return matches;
    }
}
