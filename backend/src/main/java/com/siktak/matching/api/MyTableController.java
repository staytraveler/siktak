package com.siktak.matching.api;

import java.time.Instant;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.application.application.ApplicationService;
import com.siktak.chat.application.ChatService;
import com.siktak.chat.application.TableGuidanceService;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.common.security.AuthUser;
import com.siktak.matching.application.MatchQueryService;
import com.siktak.matching.application.MatchingService;
import com.siktak.matching.domain.DiningTable;
import com.siktak.matching.domain.TableMember;
import com.siktak.venue.domain.Venue;
import com.siktak.venue.infra.VenueRepository;

import lombok.RequiredArgsConstructor;

/**
 * 참가자의 확정 테이블 조회. 다른 참가자의 개인정보·점수는 내보내지 않는다 (INF-06, MAT-12).
 */
@RestController
@RequestMapping("/api/v1/me/applications/{applicationId}/table")
@RequiredArgsConstructor
public class MyTableController {

    private final ApplicationService applicationService;
    private final MatchingService matchingService;
    private final ChatService chatService;
    private final VenueRepository venueRepository;

    @GetMapping
    public MyTableResponse table(@AuthenticationPrincipal AuthUser user, @PathVariable Long applicationId) {
        applicationService.getOwned(user.id(), applicationId);
        TableMember membership = matchingService.activeMembership(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "확정된 테이블이 없습니다."));
        DiningTable table = matchingService.findTable(membership.getDiningTableId()).orElseThrow();
        Long chatRoomId = chatService.findByTable(table.getId()).map(r -> r.getId()).orElse(null);
        List<String> reasons = List.of(MatchQueryService.CRITERIA_TEXT);
        Venue venue = table.getVenueId() == null ? null : venueRepository.findById(table.getVenueId()).orElse(null);
        return new MyTableResponse(table.getId(), table.getEventSlotId(), table.getMemberCount(),
                table.getConfirmedAt(), chatRoomId, reasons,
                venue == null ? null : venue.getName(), venue == null ? null : venue.getAddress(),
                table.getReservationName(), venue == null ? null : TableGuidanceService.mapUrl(venue));
    }

    /** 식당 정보는 이 테이블의 확정 구성원(본인 신청)에게만 내려간다. 배정 전이면 null. */
    public record MyTableResponse(Long tableId, Long slotId, int memberCount, Instant confirmedAt, Long chatRoomId,
            List<String> reasons, String venueName, String venueAddress, String reservationName, String mapUrl) {
    }
}
