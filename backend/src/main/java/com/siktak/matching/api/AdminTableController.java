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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.application.domain.status.StatusHistory;
import com.siktak.application.infra.StatusHistoryRepository;
import com.siktak.chat.application.ChatService;
import com.siktak.feedback.domain.Attendance;
import com.siktak.feedback.infra.AttendanceRepository;
import com.siktak.common.security.AuthUser;
import com.siktak.matching.application.MatchQueryService;
import com.siktak.matching.application.TableVenueService;
import com.siktak.matching.domain.DiningTable;
import com.siktak.matching.domain.TableMember;
import com.siktak.notification.infra.NotificationRepository;
import com.siktak.profile.domain.Profile;
import com.siktak.profile.infra.ProfileRepository;
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
    private final ProfileRepository profileRepository;
    private final NotificationRepository notificationRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final AttendanceRepository attendanceRepository;

    @GetMapping
    public TableDetailResponse detail(@PathVariable Long tableId) {
        return toResponse(queryService.table(tableId));
    }

    /** 단체방 메시지 전체 (운영자용). 단체방이 아직 없으면 빈 목록. */
    @GetMapping("/messages")
    public List<MessageResponse> messages(@PathVariable Long tableId) {
        queryService.table(tableId);
        return chatService.findByTable(tableId)
                .map(room -> chatService.messagesOfRoom(room.getId()).stream().map(m -> new MessageResponse(m.getId(),
                        m.getSenderType().name(), m.getMessageType().name(), m.getBody(), m.getCreatedAt())).toList())
                .orElse(List.of());
    }

    /** 테이블 구성원별 알림 발송 상태 (PENDING·SENT·FAILED). */
    @GetMapping("/notifications")
    public List<NotificationStatusResponse> notifications(@PathVariable Long tableId) {
        queryService.table(tableId);
        List<TableMember> members = queryService.members(tableId);
        Map<Long, User> users = userRepository.findAllById(members.stream().map(TableMember::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return notificationRepository.findByUserIdInOrderByIdAsc(users.keySet()).stream()
                .map(n -> new NotificationStatusResponse(n.getId(), n.getUserId(), users.get(n.getUserId()).getName(),
                        n.getTemplateCode(), n.getSendStatus().name(), n.getAttempts(), n.getLastError(),
                        n.getScheduledAt(), n.getSentAt(), n.getPushStatus()))
                .toList();
    }

    /**
     * 테이블·구성원 상태 변경 이력 (시각 순). 구성원 신청의 매칭·참석 상태 전이(status_histories)에 테이블 자체 사건
     * (확정·식당 배정)을 더한다. 주체(SYSTEM·SCHEDULER·OPERATOR 등)와 원인을 함께 보여 "운영자 개입 없음"을 확인하게 한다.
     */
    @GetMapping("/history")
    public List<HistoryEntry> history(@PathVariable Long tableId) {
        DiningTable table = queryService.table(tableId);
        List<TableMember> members = queryService.members(tableId);
        Map<Long, User> users = userRepository.findAllById(members.stream().map(TableMember::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, String> nameByApplication = members.stream().collect(Collectors.toMap(TableMember::getApplicationId,
                m -> users.get(m.getUserId()) == null ? "#" + m.getUserId() : users.get(m.getUserId()).getName(),
                (a, b) -> a));
        List<HistoryEntry> entries = new java.util.ArrayList<>();
        for (StatusHistory h : statusHistoryRepository.findByTargetTypeAndTargetIdInOrderByIdAsc(
                StatusHistory.TARGET_APPLICATION, nameByApplication.keySet())) {
            // 매칭·참석 축만. 신청·결제 전이는 테이블과 무관하다.
            if (h.getAxis().name().equals("MATCH") || h.getAxis().name().equals("ATTENDANCE")) {
                entries.add(entry(h, nameByApplication.get(h.getTargetId())));
            }
        }
        Map<Long, String> nameByAttendance = new java.util.HashMap<>();
        for (Attendance a : attendanceRepository.findByDiningTableIdOrderByIdAsc(tableId)) {
            nameByAttendance.put(a.getId(), nameByApplication.getOrDefault(a.getApplicationId(), "#" + a.getUserId()));
        }
        if (!nameByAttendance.isEmpty()) {
            for (StatusHistory h : statusHistoryRepository.findByTargetTypeAndTargetIdInOrderByIdAsc("ATTENDANCE",
                    nameByAttendance.keySet())) {
                entries.add(entry(h, nameByAttendance.get(h.getTargetId())));
            }
        }
        // [해석] 테이블 자체 사건은 별도 이력 테이블이 없어 확정·식당 배정 시각 컬럼에서 만든다 (주체: 시스템. 운영자 재배정은 예외 처리 기록 참조).
        if (table.getConfirmedAt() != null) {
            entries.add(new HistoryEntry(table.getConfirmedAt(), "테이블", "TABLE", null, table.getTableStatus().name(),
                    "TABLE_CONFIRMED", "SYSTEM", null, "자동 확정"));
        }
        if (table.getVenueAssignedAt() != null) {
            entries.add(new HistoryEntry(table.getVenueAssignedAt(), "테이블", "TABLE", null, "VENUE_ASSIGNED",
                    "VENUE_ASSIGNED", "SYSTEM", null, "식당 배정 (예약명 " + table.getReservationName() + ")"));
        }
        entries.sort(java.util.Comparator.comparing(HistoryEntry::at));
        return entries;
    }

    private static HistoryEntry entry(StatusHistory h, String who) {
        return new HistoryEntry(h.getCreatedAt(), who, h.getAxis().name(), h.getFromStatus(), h.getToStatus(),
                h.getTransitionCode(), h.getActorType().name(), h.getActorId(), h.getReason());
    }

    /** 식당 부족 예외 처리: 수용량을 늘린 뒤 다시 배정한다. 이미 배정됐으면 그대로 (멱등). 사유는 예외 기록에 남는다. */
    @PostMapping("/assign-venue")
    public TableDetailResponse assignVenue(@AuthenticationPrincipal AuthUser operator, @PathVariable Long tableId,
            @RequestBody(required = false) AssignVenueRequest request) {
        return toResponse(tableVenueService.assignVenue(tableId, operator.id(), request == null ? null : request.reason()));
    }

    private TableDetailResponse toResponse(DiningTable t) {
        List<TableMember> members = queryService.members(t.getId());
        Map<Long, User> users = userRepository.findAllById(members.stream().map(TableMember::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        VenueResponse venue = t.getVenueId() == null ? null : venueRepository.findById(t.getVenueId())
                .map(v -> new VenueResponse(v.getId(), v.getName(), v.getAddress(), v.getPriceRange()))
                .orElse(null);
        Map<Long, Profile> profiles = profileRepository.findAllById(users.keySet()).stream()
                .collect(Collectors.toMap(Profile::getUserId, Function.identity()));
        Long chatRoomId = chatService.findByTable(t.getId()).map(r -> r.getId()).orElse(null);
        return new TableDetailResponse(t.getId(), t.getEventSlotId(), t.getMatchRunId(), t.getTableStatus().name(),
                t.getMemberCount(), t.getTotalScore(), t.getScoreDetail(), t.getSummary(), venue,
                t.getReservationName(), t.getVenueAssignedAt(), t.getConfirmedAt(), chatRoomId,
                members.stream().map(m -> member(m, users, profiles)).toList());
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

    private static MemberResponse member(TableMember m, Map<Long, User> users, Map<Long, Profile> profiles) {
        User u = users.get(m.getUserId());
        Profile p = profiles.get(m.getUserId());
        return new MemberResponse(m.getApplicationId(), m.getUserId(), u == null ? null : u.getName(),
                u == null ? null : u.getLoginId(), p == null ? null : p.getBirthYear(), p == null ? null : p.getGender(),
                p == null ? null : p.getMbti(), p == null ? List.of() : p.getInterests());
    }

    public record HistoryEntry(Instant at, String target, String axis, String from, String to, String code,
            String actorType, Long actorId, String reason) {
    }

    public record AssignVenueRequest(String reason) {
    }

    public record MessageResponse(Long id, String senderType, String messageType, String body, Instant createdAt) {
    }

    public record NotificationStatusResponse(Long id, Long userId, String userName, String templateCode,
            String sendStatus, int attempts, String lastError, Instant scheduledAt, Instant sentAt,
            String pushStatus) {
    }

    /** 운영자 화면용 구성원 정보 (참가자 앱에는 노출하지 않는다). */
    public record MemberResponse(Long applicationId, Long userId, String name, String loginId, Integer birthYear,
            String gender, String mbti, List<String> interests) {
    }
}
