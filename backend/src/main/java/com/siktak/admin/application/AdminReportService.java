package com.siktak.admin.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * 운영자 조회·내보내기 (PRD 11장 신청자 목록·피드백, 16장 CSV, 8.2). 읽기 전용 집계라 여러 도메인 테이블을 SQL로 직접 읽는다.
 * <p>16장: 사람별 선호(AVOID/WANT_AGAIN)는 집계 숫자만 내보내고, 누가 누구를 골랐는지는 어디에도 싣지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportService {

    private final NamedParameterJdbcTemplate jdbc;

    public record ApplicantRow(Long applicationId, Long userId, String name, String loginId, String applicationStatus,
            String paymentStatus, String matchStatus, String attendanceStatus, Long assignedSlotId, String slotChoices,
            Instant submittedAt, Integer birthYear, String gender, String job, String mbti, long attendedCount,
            long reportedCount) {
    }

    public record TableRow(Long tableId, Long slotId, String regionName, Instant startsAt, String tableStatus,
            int memberCount, Double totalScore, String venueName, String reservationName, String members) {
    }

    public record Averages(long count, Double table, Double conversation, Double venue, Double rejoin,
            Double recommend) {
    }

    public record TableFeedback(Long tableId, Long slotId, Averages averages, long wantAgainCount, long avoidCount) {
    }

    public record CommentRow(Long tableId, Long slotId, int tableRating, String comment, Instant submittedAt) {
    }

    public record FeedbackSummary(Averages overall, long wantAgainCount, long avoidCount, List<TableFeedback> tables,
            List<CommentRow> comments) {
    }

    /** 신청자 목록. {@code slotId}가 있으면 그 회차를 고른(또는 배정된) 신청만. */
    public List<ApplicantRow> applicants(Long slotId) {
        MapSqlParameterSource params = new MapSqlParameterSource("slotId", slotId);
        String filter = slotId == null ? "" : """
                where a.assigned_slot_id = :slotId
                   or exists (select 1 from application_slot_choices f
                              where f.application_id = a.id and f.event_slot_id = :slotId)
                """;
        return jdbc.query("""
                select a.id, a.user_id, u.name, u.login_id, a.application_status, a.payment_status, a.match_status,
                       att.attendance_status, a.assigned_slot_id, a.submitted_at,
                       (select string_agg(c.event_slot_id::text, '>' order by c.priority)
                          from application_slot_choices c where c.application_id = a.id) as slot_choices,
                       p.birth_year, p.gender, p.job, p.mbti,
                       (select count(*) from attendances x
                         where x.user_id = a.user_id and x.attendance_status = 'ATTENDED') as attended_count,
                       (select count(*) from safety_reports r where r.reported_id = a.user_id) as reported_count
                from applications a
                join users u on u.id = a.user_id
                left join profiles p on p.user_id = a.user_id
                left join attendances att on att.application_id = a.id
                """ + filter + " order by a.id", params, (rs, i) -> new ApplicantRow(
                rs.getLong("id"), rs.getLong("user_id"), rs.getString("name"), rs.getString("login_id"),
                rs.getString("application_status"), rs.getString("payment_status"), rs.getString("match_status"),
                rs.getString("attendance_status"), (Long) rs.getObject("assigned_slot_id"),
                rs.getString("slot_choices"), instant(rs.getTimestamp("submitted_at")),
                (Integer) rs.getObject("birth_year"), rs.getString("gender"), rs.getString("job"),
                rs.getString("mbti"), rs.getLong("attended_count"), rs.getLong("reported_count")));
    }

    /** 테이블 결과 (확정 전 후보 테이블 포함). */
    public List<TableRow> tables(Long slotId) {
        String filter = slotId == null ? "" : "where t.event_slot_id = :slotId";
        return jdbc.query("""
                select t.id, t.event_slot_id, r.name as region_name, s.starts_at, t.table_status, t.member_count,
                       t.total_score, v.name as venue_name, t.reservation_name,
                       (select string_agg(u.name, ', ' order by u.id)
                          from table_members m join users u on u.id = m.user_id
                         where m.dining_table_id = t.id and m.active) as members
                from dining_tables t
                join event_slots s on s.id = t.event_slot_id
                join regions r on r.id = s.region_id
                left join venues v on v.id = t.venue_id
                """ + filter + " order by t.event_slot_id, t.id", new MapSqlParameterSource("slotId", slotId),
                (rs, i) -> new TableRow(rs.getLong("id"), rs.getLong("event_slot_id"), rs.getString("region_name"),
                        instant(rs.getTimestamp("starts_at")), rs.getString("table_status"), rs.getInt("member_count"),
                        rs.getBigDecimal("total_score") == null ? null : rs.getBigDecimal("total_score").doubleValue(), rs.getString("venue_name"),
                        rs.getString("reservation_name"), rs.getString("members")));
    }

    public FeedbackSummary feedbackSummary(Long slotId) {
        MapSqlParameterSource params = new MapSqlParameterSource("slotId", slotId);
        String slotFilter = slotId == null ? "" : " and t.event_slot_id = :slotId";

        Averages overall = jdbc.query("""
                select count(*) c, avg(f.table_rating) t, avg(f.conversation_rating) cv, avg(f.venue_rating) v,
                       avg(f.rejoin_intent) rj, avg(f.recommend_score) rc
                from feedbacks f join dining_tables t on t.id = f.dining_table_id
                where true """ + slotFilter, params, rs -> {
            rs.next();
            return averages(rs.getLong("c"), rs.getObject("t"), rs.getObject("cv"), rs.getObject("v"),
                    rs.getObject("rj"), rs.getObject("rc"));
        });

        Map<Long, long[]> prefs = new HashMap<>(); // tableId -> {wantAgain, avoid}
        jdbc.query("""
                select p.dining_table_id, p.preference, count(*) c
                from person_preferences p join dining_tables t on t.id = p.dining_table_id
                where true """ + slotFilter + " group by p.dining_table_id, p.preference", params, rs -> {
            long[] counts = prefs.computeIfAbsent(rs.getLong("dining_table_id"), k -> new long[2]);
            counts["WANT_AGAIN".equals(rs.getString("preference")) ? 0 : 1] += rs.getLong("c");
        });

        List<TableFeedback> tables = new ArrayList<>();
        jdbc.query("""
                select f.dining_table_id, t.event_slot_id, count(*) c, avg(f.table_rating) t,
                       avg(f.conversation_rating) cv, avg(f.venue_rating) v, avg(f.rejoin_intent) rj,
                       avg(f.recommend_score) rc
                from feedbacks f join dining_tables t on t.id = f.dining_table_id
                where true """ + slotFilter + " group by f.dining_table_id, t.event_slot_id order by f.dining_table_id",
                params, rs -> {
            long tableId = rs.getLong("dining_table_id");
            long[] p = prefs.getOrDefault(tableId, new long[2]);
            tables.add(new TableFeedback(tableId, rs.getLong("event_slot_id"),
                    averages(rs.getLong("c"), rs.getObject("t"), rs.getObject("cv"), rs.getObject("v"),
                            rs.getObject("rj"), rs.getObject("rc")),
                    p[0], p[1]));
        });

        // 코멘트는 작성자를 싣지 않는다 (테이블 번호만)
        List<CommentRow> comments = jdbc.query("""
                select f.dining_table_id, t.event_slot_id, f.table_rating, f.comment, f.submitted_at
                from feedbacks f join dining_tables t on t.id = f.dining_table_id
                where f.comment is not null and f.comment <> '' """ + slotFilter + " order by f.submitted_at desc",
                params, (rs, i) -> new CommentRow(rs.getLong("dining_table_id"), rs.getLong("event_slot_id"),
                        rs.getInt("table_rating"), rs.getString("comment"), instant(rs.getTimestamp("submitted_at"))));

        long wantAgain = prefs.values().stream().mapToLong(c -> c[0]).sum();
        long avoid = prefs.values().stream().mapToLong(c -> c[1]).sum();
        return new FeedbackSummary(overall, wantAgain, avoid, tables, comments);
    }

    // ---- CSV (PRD 16장). 엑셀 한글 깨짐 방지로 UTF-8 BOM을 앞에 붙인다.

    public String applicantsCsv(Long slotId) {
        CsvWriter csv = new CsvWriter("신청번호", "사용자번호", "이름", "로그인ID", "신청상태", "결제상태", "매칭상태", "참석상태",
                "배정회차", "희망회차(우선순위순)", "신청시각", "출생연도", "성별", "직업", "MBTI", "이전참석횟수", "신고받은횟수");
        for (ApplicantRow a : applicants(slotId)) {
            csv.row(a.applicationId(), a.userId(), a.name(), a.loginId(), a.applicationStatus(), a.paymentStatus(),
                    a.matchStatus(), a.attendanceStatus(), a.assignedSlotId(), a.slotChoices(), a.submittedAt(),
                    a.birthYear(), a.gender(), a.job(), a.mbti(), a.attendedCount(), a.reportedCount());
        }
        return csv.build();
    }

    public String tablesCsv(Long slotId) {
        CsvWriter csv = new CsvWriter("테이블번호", "회차", "지역", "시작", "상태", "인원", "총점", "식당", "예약명", "참가자");
        for (TableRow t : tables(slotId)) {
            csv.row(t.tableId(), t.slotId(), t.regionName(), t.startsAt(), t.tableStatus(), t.memberCount(),
                    t.totalScore(), t.venueName(), t.reservationName(), t.members());
        }
        return csv.build();
    }

    private static Averages averages(long count, Object table, Object conversation, Object venue, Object rejoin,
            Object recommend) {
        return new Averages(count, avg(table), avg(conversation), avg(venue), avg(rejoin), avg(recommend));
    }

    private static Double avg(Object value) {
        return value instanceof Number n ? Math.round(n.doubleValue() * 100) / 100.0 : null;
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    /** 쉼표·따옴표·줄바꿈을 감싸고, 엑셀 수식 주입(=,+,-,@로 시작)은 앞에 작은따옴표를 붙여 막는다. */
    static final class CsvWriter {
        private static final String BOM = "﻿";
        private final StringBuilder out = new StringBuilder(BOM);

        CsvWriter(String... header) {
            row((Object[]) header);
        }

        void row(Object... cells) {
            for (int i = 0; i < cells.length; i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(escape(cells[i]));
            }
            out.append("\r\n");
        }

        String build() {
            return out.toString();
        }

        static String escape(Object cell) {
            if (cell == null) {
                return "";
            }
            String s = cell.toString();
            if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 && !(cell instanceof Number)) {
                s = "'" + s;
            }
            if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
                s = "\"" + s.replace("\"", "\"\"") + "\"";
            }
            return s;
        }
    }
}
