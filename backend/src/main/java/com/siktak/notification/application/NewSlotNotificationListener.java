package com.siktak.notification.application;

import java.util.List;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.siktak.common.config.ClockConfig;
import com.siktak.slot.domain.SlotCreatedEvent;

import lombok.RequiredArgsConstructor;

/**
 * 다음 모집 시작 알림 (PRD 14장 "다음 모집 시작: 선호 지역·요일에 맞는 다음 일정 추천", RET-02).
 * 새 회차가 열리면, 이전에 참석한 참가자 중 같은 지역에 참석했거나 같은 요일에 참석한 사람에게 알린다.
 * 회차·참가자당 1건(dedup)이라 같은 알림이 두 번 나가지 않는다.
 */
@Component
@RequiredArgsConstructor
public class NewSlotNotificationListener {

    private final JdbcTemplate jdbc;
    private final NotificationService notificationService;

    @EventListener
    public void on(SlotCreatedEvent event) {
        int dow = event.startsAt().atZone(ClockConfig.ZONE).getDayOfWeek().getValue() % 7; // 일=0 … 토=6 (Postgres dow)
        List<Long> userIds = jdbc.query("""
                select distinct att.user_id
                from attendances att
                join dining_tables t on t.id = att.dining_table_id
                join event_slots s on s.id = t.event_slot_id
                where att.attendance_status = 'ATTENDED'
                  and (s.region_id = ? or extract(dow from s.starts_at at time zone 'Asia/Seoul') = ?)
                """, (rs, i) -> rs.getLong(1), event.regionId(), dow);
        for (Long userId : userIds) {
            notificationService.enqueue(userId, "NEXT_SLOT_RECOMMEND", Map.of(),
                    Map.of("slotIds", List.of(event.slotId()), "link", "/app/slots"),
                    "NEXT_SLOT_OPEN:slot-" + event.slotId() + ":user-" + userId);
        }
    }
}
