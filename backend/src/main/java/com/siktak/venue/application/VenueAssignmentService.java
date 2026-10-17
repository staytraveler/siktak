package com.siktak.venue.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.exception.application.ExceptionCaseService;
import com.siktak.exception.domain.CaseType;
import com.siktak.venue.domain.Venue;
import com.siktak.venue.domain.VenueSlotCapacity;
import com.siktak.venue.domain.assign.TableToAssign;
import com.siktak.venue.domain.assign.UnassignedTable;
import com.siktak.venue.domain.assign.VenueAssigner;
import com.siktak.venue.domain.assign.VenueAssignment;
import com.siktak.venue.domain.assign.VenueAssignmentResult;
import com.siktak.venue.domain.assign.VenueStock;
import com.siktak.venue.infra.VenueRepository;
import com.siktak.venue.infra.VenueSlotCapacityRepository;

import lombok.RequiredArgsConstructor;

/**
 * 식당 자동 배정 (PRD INF-02, 15장). 회차 지역의 식당 재고로 {@link VenueAssigner}를 돌리고 수용량을 차감한다.
 * 같은 지역에 배정할 식당이 없으면 {@code VENUE_UNAVAILABLE} 예외를 남긴다. 테이블 확정은 그대로 유효하다.
 */
@Service
@RequiredArgsConstructor
public class VenueAssignmentService {

    private final VenueRepository venueRepository;
    private final VenueSlotCapacityRepository capacityRepository;
    private final ExceptionCaseService exceptionCaseService;

    /**
     * @param tables 배정할 테이블. 예약명 순번은 이 목록의 tableId 순 위치라 회차 첫 배정 때 회차 테이블 전체를 넘긴다
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public VenueAssignmentResult assign(Long slotId, Long regionId, LocalDate slotDate, List<TableToAssign> tables) {
        Map<Long, Venue> venues = venueRepository.findByRegionIdOrderByIdAsc(regionId).stream()
                .collect(Collectors.toMap(Venue::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<Long, VenueSlotCapacity> capacities = new LinkedHashMap<>();
        for (VenueSlotCapacity c : capacityRepository.findByEventSlotIdOrderByVenueIdAsc(slotId)) {
            if (venues.containsKey(c.getVenueId())) {
                capacities.put(c.getVenueId(), c);
            }
        }
        List<VenueStock> stocks = new ArrayList<>();
        for (VenueSlotCapacity c : capacities.values()) {
            Venue v = venues.get(c.getVenueId());
            stocks.add(new VenueStock(v.getId(), c.remainingTables(), c.getMaxSeatsPerTable(), v.getPriceRange(),
                    v.isActive()));
        }

        VenueAssignmentResult result = VenueAssigner.assign(slotDate, tables, stocks);
        for (VenueAssignment a : result.assigned()) {
            capacities.get(a.venueId()).reserveOne();
        }
        Map<Long, Integer> sizes = tables.stream()
                .collect(Collectors.toMap(TableToAssign::tableId, TableToAssign::memberCount));
        for (UnassignedTable u : result.unassigned()) {
            exceptionCaseService.open(CaseType.VENUE_UNAVAILABLE, ExceptionCaseService.venueUnavailableKey(u.tableId()),
                    null, u.tableId(),
                    "같은 지역에 배정 가능한 식당이 없습니다. 수용량을 늘리거나 식당을 추가한 뒤 재배정하세요.",
                    Map.of("slotId", slotId, "regionId", regionId, "memberCount", sizes.get(u.tableId()),
                            "reservationName", u.reservationName()));
        }
        return result;
    }
}
