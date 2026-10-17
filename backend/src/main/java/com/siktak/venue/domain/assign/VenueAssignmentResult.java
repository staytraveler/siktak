package com.siktak.venue.domain.assign;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 식당 배정 결과.
 *
 * @param assigned         tableId 오름차순
 * @param unassigned       tableId 오름차순. 같은 지역 식당이 모두 안 될 때만 생긴다 (PRD 15장)
 * @param remainingTables  배정 후 식당별 남은 테이블 수 (venueId 오름차순)
 */
public record VenueAssignmentResult(
        List<VenueAssignment> assigned,
        List<UnassignedTable> unassigned,
        Map<Long, Integer> remainingTables) {

    public VenueAssignmentResult {
        assigned = List.copyOf(assigned);
        unassigned = List.copyOf(unassigned);
        remainingTables = Collections.unmodifiableMap(new TreeMap<>(remainingTables));
    }
}
