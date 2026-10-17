package com.siktak.matching.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.chat.application.ChatService;
import com.siktak.common.security.AuthUser;
import com.siktak.matching.application.MatchQueryService;
import com.siktak.matching.application.TableVenueService;
import com.siktak.matching.domain.DiningTable;
import com.siktak.matching.domain.TableMember;
import com.siktak.user.domain.User;
import com.siktak.user.infra.UserRepository;
import com.siktak.venue.domain.Venue;
import com.siktak.venue.infra.VenueRepository;

import lombok.RequiredArgsConstructor;

/** 운영자 테이블 상세 (구성·점수·PRD 7.2 요약·식당·단체방)와 식당 재배정. 화면은 8.x. */
@RestController
@RequestMapping("/api/v1/admin/tables/{tableId}")
@RequiredArgsConstructor
public class AdminTableController {

    private final MatchQueryService queryService;
    private final TableVenueService tableVenueService;
    private final ChatService chatService;
    private final UserRepository userRepository;
    private final VenueRepository venueRepository;

    @GetMapping
    public TableDetailResponse detail(@PathVariable Long tableId) {
        return toResponse(queryService.table(tableId));
    }

    /** 식당 부족 예외 처리: 수용량을 늘린 뒤 다시 배정한다. 이미 배정됐으면 그대로 (멱등). */
    @PostMapping("/assign-venue")
    public TableDetailResponse assignVenue(@AuthenticationPrincipal AuthUser operator, @PathVariable Long tableId) {
        return toResponse(tableVenueService.assignVenue(tableId, operator.id()));
    }

    private TableDetailResponse toResponse(DiningTable t) {
        List<TableMember> members = queryService.members(t.getId());
        Map<Long, User> users = userRepository.findAllById(members.stream().map(TableMember::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        VenueResponse venue = t.getVenueId() == null ? null : venueRepository.findById(t.getVenueId())
                .map(v -> new VenueResponse(v.getId(), v.getName(), v.getAddress(), v.getPriceRange()))
                .orElse(null);
        Long chatRoomId = chatService.findByTable(t.getId()).map(r -> r.getId()).orElse(null);
        return new TableDetailResponse(t.getId(), t.getEventSlotId(), t.getMatchRunId(), t.getTableStatus().name(),
                t.getMemberCount(), t.getTotalScore(), t.getScoreDetail(), t.getSummary(), venue,
                t.getReservationName(), t.getVenueAssignedAt(), t.getConfirmedAt(), chatRoomId,
                members.stream().map(m -> new MemberResponse(m.getApplicationId(), m.getUserId(),
                        users.containsKey(m.getUserId()) ? users.get(m.getUserId()).getName() : null)).toList());
    }

    public record TableDetailResponse(Long id, Long slotId, Long matchRunId, String tableStatus, int memberCount,
            BigDecimal totalScore, Map<String, Object> scoreDetail, Map<String, Object> summary, VenueResponse venue,
            String reservationName, Instant venueAssignedAt, Instant confirmedAt, Long chatRoomId,
            List<MemberResponse> members) {
    }

    public record VenueResponse(Long id, String name, String address, String priceRange) {

        static VenueResponse from(Venue v) {
            return new VenueResponse(v.getId(), v.getName(), v.getAddress(), v.getPriceRange());
        }
    }

    public record MemberResponse(Long applicationId, Long userId, String name) {
    }
}
