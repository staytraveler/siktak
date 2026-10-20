package com.siktak.chat.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * 내가 참여 중인 단체방 목록 (INF-04, 참가자 앱 "대화" 탭). 읽기 전용 SQL로 방·행사·식당·마지막 메시지를 한 번에 모은다.
 * 다른 참가자의 개인정보는 싣지 않는다 (인원 수만).
 */
@Service
@RequiredArgsConstructor
public class ChatRoomListService {

    private final JdbcTemplate jdbc;

    public record ChatRoomSummary(Long chatRoomId, Long diningTableId, Long applicationId, String roomStatus,
            Instant startsAt, String regionName, String venueName, int memberCount, String lastMessage,
            String lastMessageType, Instant lastMessageAt, int messageCount) {
    }

    @Transactional(readOnly = true)
    public List<ChatRoomSummary> mine(Long userId) {
        return jdbc.query("""
                select r.id as room_id, r.dining_table_id, r.room_status, s.starts_at, reg.name as region_name,
                       v.name as venue_name, t.member_count,
                       (select x.application_id from table_members x
                         where x.dining_table_id = t.id and x.user_id = cm.user_id order by x.id desc limit 1) as application_id,
                       (select m.body from chat_messages m where m.chat_room_id = r.id order by m.id desc limit 1) as last_body,
                       (select m.message_type from chat_messages m where m.chat_room_id = r.id order by m.id desc limit 1) as last_type,
                       (select m.created_at from chat_messages m where m.chat_room_id = r.id order by m.id desc limit 1) as last_at,
                       (select count(*) from chat_messages m where m.chat_room_id = r.id) as message_count
                from chat_members cm
                join chat_rooms r on r.id = cm.chat_room_id
                join dining_tables t on t.id = r.dining_table_id
                join event_slots s on s.id = t.event_slot_id
                join regions reg on reg.id = s.region_id
                left join venues v on v.id = t.venue_id
                where cm.user_id = ?
                order by (select max(m.id) from chat_messages m where m.chat_room_id = r.id) desc nulls last, r.id desc
                """, (rs, i) -> new ChatRoomSummary(rs.getLong("room_id"), rs.getLong("dining_table_id"),
                (Long) rs.getObject("application_id"), rs.getString("room_status"),
                instant(rs.getTimestamp("starts_at")), rs.getString("region_name"), rs.getString("venue_name"),
                rs.getInt("member_count"), rs.getString("last_body"), rs.getString("last_type"),
                instant(rs.getTimestamp("last_at")), rs.getInt("message_count")), userId);
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
