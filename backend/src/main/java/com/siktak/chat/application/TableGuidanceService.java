package com.siktak.chat.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.chat.domain.ChatMessage.MessageType;
import com.siktak.chat.domain.ChatRoom;
import com.siktak.common.schedule.ScheduledJob;
import com.siktak.common.schedule.ScheduledJobRegistry;
import com.siktak.content.domain.ConversationContent;
import com.siktak.content.domain.SelectedContent;
import com.siktak.content.domain.TopicSelector;
import com.siktak.content.infra.ConversationContentReader;
import com.siktak.matching.domain.DiningTable;
import com.siktak.matching.domain.TableMember;
import com.siktak.matching.domain.TableStatus;
import com.siktak.matching.infra.DiningTableRepository;
import com.siktak.matching.infra.TableMemberRepository;
import com.siktak.notification.application.GuidanceFormat;
import com.siktak.notification.application.NotificationService;
import com.siktak.profile.domain.Interest;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.infra.EventSlotRepository;
import com.siktak.venue.domain.Venue;
import com.siktak.venue.infra.VenueRepository;

import lombok.RequiredArgsConstructor;

/**
 * 테이블 확정 뒤의 모임 안내 자동화 (PRD INF-03·05·07, CON-01~03). 모든 메서드는 멱등이다 —
 * 단체방 메시지는 {@code message_key}, 알림은 {@code dedup_key}로 두 번 나가지 않는다.
 * <ul>
 *   <li>{@link #announce}: 확정(+식당 배정) 직후 시간·장소 공지, 큐레이션, 취소 정책, 확정 알림, 리마인드·콘텐츠 예약</li>
 *   <li>{@link #postReminder}: 행사 1일 전 (REMINDER_D1 작업)</li>
 *   <li>{@link #postContent}: 행사 시작 시각 (CONTENT_POST 작업)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TableGuidanceService {

    public static final String REMINDER_JOB = "REMINDER_D1";
    public static final String CONTENT_JOB = "CONTENT_POST";
    static final Duration REMINDER_BEFORE = Duration.ofDays(1);

    /** [해석] PRD 8장·PAY-02의 환불·취소·노쇼 규정 문구. 환불 가능 시점(진행 2일 전)은 prd.md 63행 그대로. */
    static final String POLICY_TEXT = "취소·환불 안내: 진행 2일 전 이후에는 환불이 불가해요. 당일 취소와 단체방 퇴장은 할 수 없고, "
            + "사전 취소 없이 불참(노쇼)하면 다음 신청에 불이익이 있을 수 있어요.";

    private final DiningTableRepository diningTableRepository;
    private final TableMemberRepository tableMemberRepository;
    private final EventSlotRepository eventSlotRepository;
    private final VenueRepository venueRepository;
    private final ChatService chatService;
    private final NotificationService notificationService;
    private final ConversationContentReader contentReader;
    private final ScheduledJobRegistry jobRegistry;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public static String key(Long tableId, String suffix) {
        return "table-" + tableId + ":" + suffix;
    }

    // ------------------------------------------------------------------ 확정 직후

    @Transactional
    public void announce(Long tableId) {
        Target t = target(tableId);
        if (t == null) {
            return;
        }
        ChatRoom room = chatService.openForTable(tableId,
                t.members.stream().map(TableMember::getUserId).toList());
        EventSlot slot = t.slot;
        Venue venue = t.venue;

        // 시간·장소 (INF-03·05). 식당이 아직 없으면 "확정 후 안내"로 먼저 알리고, 배정되면 장소 공지를 추가로 올린다.
        if (venue != null) {
            chatService.postSystemMessage(room.getId(), MessageType.NOTICE, venueNotice(slot, venue, t.table),
                    key(tableId, "guide:venue"), venuePayload(slot, venue, t.table));
        } else {
            chatService.postSystemMessage(room.getId(), MessageType.NOTICE,
                    "모임 안내\n일시: " + GuidanceFormat.dateTime(slot.getStartsAt()) + " ~ "
                            + GuidanceFormat.time(slot.getEndsAt()) + "\n장소: 확정 후 안내 (식당을 배정하는 중이에요. "
                            + "배정되면 이 방에 다시 알려드려요)",
                    key(tableId, "guide:pending"), Map.of("startsAt", slot.getStartsAt().toString()));
        }
        chatService.postSystemMessage(room.getId(), MessageType.CURATION, curation(t.table),
                key(tableId, "curation"), curationPayload(t.table));
        chatService.postSystemMessage(room.getId(), MessageType.NOTICE, POLICY_TEXT, key(tableId, "policy"), null);

        // 확정 알림 (INF-01): 멤버마다 1건
        for (TableMember m : t.members) {
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("slotLabel", GuidanceFormat.slotLabel(slot));
            vars.put("venueLine", venue == null ? "확정 후 안내" : venue.getName() + " (" + venue.getAddress() + ")");
            vars.put("gatherTime", GuidanceFormat.dateTime(slot.getStartsAt()));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("applicationId", m.getApplicationId());
            payload.put("tableId", tableId);
            payload.put("chatRoomId", room.getId());
            payload.put("link", "/app/applications/" + m.getApplicationId());
            notificationService.enqueue(m.getUserId(), "MATCH_CONFIRMED", vars, payload,
                    "MATCH_CONFIRMED:app-" + m.getApplicationId());
        }

        // 리마인드(1일 전)와 대화 콘텐츠(시작 시각)는 작업으로 예약한다 (DB 폴링 금지)
        jobRegistry.schedule(new ScheduledJob(REMINDER_JOB, tableId, reminderAt(slot)));
        jobRegistry.schedule(new ScheduledJob(CONTENT_JOB, tableId, slot.getStartsAt()));
    }

    // ------------------------------------------------------------------ 행사 1일 전

    @Transactional
    public void postReminder(Long tableId) {
        Target t = target(tableId);
        if (t == null || !t.slot.getEndsAt().isAfter(Instant.now(clock))) {
            return;
        }
        ChatRoom room = chatService.openForTable(tableId, t.members.stream().map(TableMember::getUserId).toList());
        String place = t.venue == null ? "확정 후 안내"
                : t.venue.getName() + " (" + t.venue.getAddress() + ")";
        String reservation = t.table.getReservationName() == null ? "확정 후 안내" : t.table.getReservationName();
        chatService.postSystemMessage(room.getId(), MessageType.REMINDER,
                "내일 모임 리마인드\n일시: " + GuidanceFormat.dateTime(t.slot.getStartsAt()) + "\n장소: " + place
                        + "\n예약명: " + reservation + "\n" + POLICY_TEXT,
                key(tableId, "reminder-d1"), Map.of("startsAt", t.slot.getStartsAt().toString()));
        for (TableMember m : t.members) {
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("slotLabel", GuidanceFormat.slotLabel(t.slot));
            vars.put("venueLine", place);
            vars.put("reservationName", reservation);
            notificationService.enqueue(m.getUserId(), "REMINDER_D1", vars,
                    Map.of("applicationId", m.getApplicationId(), "chatRoomId", room.getId(),
                            "link", "/app/applications/" + m.getApplicationId()),
                    "REMINDER_D1:app-" + m.getApplicationId());
        }
    }

    // ------------------------------------------------------------------ 행사 시작 시각

    @Transactional
    public void postContent(Long tableId) {
        Target t = target(tableId);
        if (t == null || !t.slot.getEndsAt().isAfter(Instant.now(clock))) {
            return;
        }
        ChatRoom room = chatService.openForTable(tableId, t.members.stream().map(TableMember::getUserId).toList());
        List<Long> userIds = t.members.stream().map(TableMember::getUserId).toList();
        List<ConversationContent> pool = contentReader.activePool();
        Map<Long, ConversationContent> byId = new LinkedHashMap<>();
        pool.forEach(c -> byId.put(c.id(), c));
        List<SelectedContent> selected = TopicSelector.select(tableId, commonInterests(t.table),
                contentReader.memberStyles(userIds), pool);

        List<Map<String, Object>> topics = new ArrayList<>();
        Map<String, Object> icebreaker = null;
        StringBuilder body = new StringBuilder("오늘의 대화 주제예요.");
        int n = 0;
        for (SelectedContent s : selected) {
            ConversationContent c = byId.get(s.contentId());
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("contentId", c.id());
            card.put("title", c.title());
            card.put("body", c.body());
            if (s.type() == com.siktak.content.domain.ContentType.ICEBREAKER) {
                icebreaker = card;
                body.append("\n\n아이스브레이커 · ").append(c.title()).append("\n").append(c.body());
            } else {
                topics.add(card);
                body.append("\n\n").append(++n).append(". ").append(c.title()).append("\n").append(c.body());
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("topics", topics);
        payload.put("icebreaker", icebreaker);
        chatService.postSystemMessage(room.getId(), MessageType.CONTENT, body.toString(),
                key(tableId, "content"), payload);
    }

    // ------------------------------------------------------------------ 기동 시 적재

    /** 아직 리마인드를 못 올린 확정 테이블 (끝나지 않은 행사). */
    @Transactional(readOnly = true)
    public List<ScheduledJob> pendingReminders() {
        return pending("reminder-d1", REMINDER_JOB, REMINDER_BEFORE);
    }

    /** 아직 대화 콘텐츠를 못 올린 확정 테이블 (끝나지 않은 행사). */
    @Transactional(readOnly = true)
    public List<ScheduledJob> pendingContents() {
        return pending("content", CONTENT_JOB, Duration.ZERO);
    }

    private List<ScheduledJob> pending(String keySuffix, String jobType, Duration before) {
        return jdbc.query("""
                select t.id, s.starts_at
                from dining_tables t join event_slots s on s.id = t.event_slot_id
                where t.table_status = 'CONFIRMED' and s.ends_at > ?
                  and not exists (select 1 from chat_messages m where m.message_key = 'table-' || t.id || ':' || ?)
                order by t.id
                """, (rs, i) -> new ScheduledJob(jobType, rs.getLong("id"),
                rs.getTimestamp("starts_at").toInstant().minus(before)),
                Timestamp.from(Instant.now(clock)), keySuffix);
    }

    // ------------------------------------------------------------------ 내부

    private Target target(Long tableId) {
        DiningTable table = diningTableRepository.findById(tableId).orElse(null);
        if (table == null || table.getTableStatus() != TableStatus.CONFIRMED) {
            return null;
        }
        EventSlot slot = eventSlotRepository.findById(table.getEventSlotId()).orElseThrow();
        Venue venue = table.getVenueId() == null ? null : venueRepository.findById(table.getVenueId()).orElse(null);
        return new Target(table, slot, venue, tableMemberRepository.findByDiningTableIdAndActiveTrue(tableId));
    }

    private record Target(DiningTable table, EventSlot slot, Venue venue, List<TableMember> members) {
    }

    private static Instant reminderAt(EventSlot slot) {
        return slot.getStartsAt().minus(REMINDER_BEFORE);
    }

    /** [해석] 집합 시간 = 모임 시작 시각 (PRD에 별도 집합 시각 규칙이 없음). */
    private static String venueNotice(EventSlot slot, Venue venue, DiningTable table) {
        StringBuilder sb = new StringBuilder("모임 안내\n");
        sb.append("일시: ").append(GuidanceFormat.dateTime(slot.getStartsAt())).append(" ~ ")
                .append(GuidanceFormat.time(slot.getEndsAt())).append("\n");
        sb.append("장소: ").append(venue.getName()).append(" (").append(venue.getAddress()).append(")\n");
        {
            sb.append("지도: ").append(venue.getMapUrl()).append("\n");
        }
        sb.append("예약명: ").append(table.getReservationName() == null ? "확정 후 안내" : table.getReservationName())
                .append("\n");
        sb.append("집합 시간: ").append(GuidanceFormat.time(slot.getStartsAt())).append("\n");
        sb.append("식사 가격: ").append(GuidanceFormat.priceRange(venue.getPriceRange()));
        return sb.toString();
    }

    /** [해석] 식당에 지도 링크가 없으면 식당명+주소 검색 링크로 대신한다 (INF-03 지도 링크 필수). */
    static String mapUrl(Venue venue) {
        if (venue.getMapUrl() != null && !venue.getMapUrl().isBlank()) {
            return venue.getMapUrl();
        }
        return "https://map.naver.com/p/search/"
                + java.net.URLEncoder.encode(venue.getName() + " " + venue.getAddress(),
                        java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static Map<String, Object> venuePayload(EventSlot slot, Venue venue, DiningTable table) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("venueName", venue.getName());
        p.put("address", venue.getAddress());
        p.put("mapUrl", mapUrl(venue));
        p.put("reservationName", table.getReservationName());
        p.put("startsAt", slot.getStartsAt().toString());
        p.put("gatherTime", GuidanceFormat.time(slot.getStartsAt()));
        p.put("priceRange", GuidanceFormat.priceRange(venue.getPriceRange()));
        return p;
    }

    /** 큐레이션: 테이블 전체의 집계만. 개인 정보·선호·점수는 싣지 않는다 (INF-06). */
    private static String curation(DiningTable table) {
        Map<String, Object> s = table.getSummary();
        StringBuilder sb = new StringBuilder("이 테이블은 ").append(table.getMemberCount()).append("명이 함께해요.");
        Map<String, Object> age = map(s.get("age"));
        if (age != null) {
            sb.append("\n연령대: ").append(age.get("minBirthYear")).append("~").append(age.get("maxBirthYear"))
                    .append("년생");
        }
        Map<String, Object> gender = map(s.get("gender"));
        if (gender != null) {
            sb.append("\n성비: 남 ").append(gender.get("male")).append(" · 여 ").append(gender.get("female"));
        }
        List<String> labels = commonInterests(table).stream().map(Interest::label).toList();
        sb.append("\n공통 관심사: ").append(labels.isEmpty() ? "다양한 취향이 모였어요" : String.join(", ", labels));
        return sb.toString();
    }

    private static Map<String, Object> curationPayload(DiningTable table) {
        Map<String, Object> s = table.getSummary();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("memberCount", table.getMemberCount());
        p.put("age", s.get("age"));
        p.put("gender", s.get("gender"));
        p.put("commonInterests", commonInterests(table).stream().map(Enum::name).toList());
        return p;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    private static List<Interest> commonInterests(DiningTable table) {
        Object raw = table.getSummary().get("commonInterests");
        List<Interest> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                try {
                    result.add(Interest.valueOf(String.valueOf(o)));
                } catch (IllegalArgumentException ignored) {
                    // 알 수 없는 관심사 코드는 건너뛴다
                }
            }
        }
        return result;
    }
}
