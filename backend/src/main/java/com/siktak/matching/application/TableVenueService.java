package com.siktak.matching.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.common.config.ClockConfig;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.exception.application.ExceptionCaseService;
import com.siktak.matching.domain.DiningTable;
import com.siktak.matching.domain.TableMember;
import com.siktak.matching.infra.DiningTableRepository;
import com.siktak.matching.infra.MatchingDataReader;
import com.siktak.matching.infra.TableMemberRepository;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.infra.EventSlotRepository;
import com.siktak.venue.application.VenueAssignmentService;
import com.siktak.venue.domain.assign.VenueAssignment;
import com.siktak.venue.domain.assign.VenueAssignmentResult;

import lombok.RequiredArgsConstructor;

/** 운영자 식당 재배정 (PRD 15장 식당 부족 예외 처리). 수용량을 늘린 뒤 다시 시도한다. */
@Service
@RequiredArgsConstructor
public class TableVenueService {

    private final DiningTableRepository diningTableRepository;
    private final TableMemberRepository tableMemberRepository;
    private final EventSlotRepository eventSlotRepository;
    private final MatchingDataReader dataReader;
    private final EngineMatchingService engineMatchingService;
    private final VenueAssignmentService venueAssignmentService;
    private final ExceptionCaseService exceptionCaseService;
    private final Clock clock;

    /**
     * 이미 식당이 있으면 그대로 돌려준다 (멱등). 배정되면 식당 미배정 예외를 해결 처리한다.
     * 예약명은 처음 정한 값을 그대로 쓴다.
     */
    @Transactional
    public DiningTable assignVenue(Long tableId, Long operatorId) {
        DiningTable table = diningTableRepository.findById(tableId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "테이블을 찾을 수 없습니다."));
        if (table.hasVenue()) {
            return table;
        }
        EventSlot slot = eventSlotRepository.findById(table.getEventSlotId()).orElseThrow();
        List<Long> userIds = tableMemberRepository.findByDiningTableIdAndActiveTrue(tableId).stream()
                .map(TableMember::getUserId).toList();
        VenueAssignmentResult result = venueAssignmentService.assign(slot.getId(), slot.getRegion().getId(),
                slot.getStartsAt().atZone(ClockConfig.ZONE).toLocalDate(),
                List.of(engineMatchingService.tableToAssign(table, dataReader.profiles(userIds))));
        if (result.assigned().isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "배정 가능한 식당이 없습니다. 수용량을 먼저 늘려 주세요.",
                    Map.of("tableId", tableId, "slotId", slot.getId()));
        }
        VenueAssignment assigned = result.assigned().get(0);
        table.proposeReservationName(assigned.reservationName());
        table.assignVenue(assigned.venueId(), Instant.now(clock));
        exceptionCaseService.resolve(ExceptionCaseService.venueUnavailableKey(tableId), operatorId,
                "운영자 재배정으로 식당 배정 완료 (venue " + assigned.venueId() + ")");
        return table;
    }
}
