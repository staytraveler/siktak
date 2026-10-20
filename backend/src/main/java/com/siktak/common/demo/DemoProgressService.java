package com.siktak.common.demo;

import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * 시연 가이드의 "지금 어디까지 왔나" 판정. 클릭 기록이 아니라 실제 DB 상태에서 단계 완료를 계산하므로,
 * 도중에 실패하거나 순서가 꼬여도 정확한 위치에서 이어갈 수 있다. 읽기 전용.
 */
@Service
@RequiredArgsConstructor
public class DemoProgressService {

    private final JdbcTemplate jdbc;
    private final DemoService demoService;

    public record Progress(Instant now, long offsetSeconds, List<DemoService.JumpTarget> jumps,
            Long demoApplicationId, String demoPayment, String demoMatch, String demoAttendance,
            boolean demoFeedback, boolean demoAvoid, boolean user14Offered, boolean user14Transferred,
            boolean user23Offered, boolean user23Refunded, boolean round2Applied,
            Stats stats) {
    }

    /** 시스템이 자동으로 한 일 / 사람이 한 일 (화면에 숫자로 보여준다). */
    public record Stats(long autoActions, long operatorActions, long participantActions, long matchRuns,
            long tablesConfirmed, long venuesAssigned, long chatRooms, long notificationsSent, long remindersSent,
            long contentsPosted, long refundsDone, long reallocated, long openExceptions, long venueExceptions,
            long noShowCandidates, long applicants, long paid) {
    }

    @Transactional(readOnly = true)
    public Progress progress() {
        DemoService.DemoStatus status = demoService.status();
        // 1회차(가장 먼저 낸) 신청을 기준으로 삼는다. 2회차 신청이 생겨도 앞 단계 판정이 되돌아가지 않게.
        var demoApp = jdbc.query("""
                select a.id, a.payment_status, a.match_status, att.attendance_status
                from applications a join users u on u.id = a.user_id
                left join attendances att on att.application_id = a.id
                where u.login_id = 'demo01'
                order by a.id asc limit 1
                """, rs -> rs.next()
                ? new String[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)} : null);
        Long demoAppId = demoApp == null ? null : Long.valueOf(demoApp[0]);
        boolean feedback = demoAppId != null && exists("select 1 from feedbacks where application_id = ?", demoAppId);
        boolean avoid = exists("""
                select 1 from person_preferences p join users u on u.id = p.from_user_id
                where u.login_id = 'demo01' and p.preference = 'AVOID'
                """);
        Stats stats = new Stats(
                count("select count(*) from status_histories where actor_type in ('SYSTEM','SCHEDULER','PAYMENT_GATEWAY')"),
                count("select count(*) from status_histories where actor_type = 'OPERATOR'"),
                count("select count(*) from status_histories where actor_type = 'PARTICIPANT'"),
                count("select count(*) from match_runs"),
                count("select count(*) from dining_tables where table_status = 'CONFIRMED'"),
                count("select count(*) from dining_tables where table_status = 'CONFIRMED' and venue_id is not null"),
                count("select count(*) from chat_rooms"),
                count("select count(*) from notifications where send_status = 'SENT'"),
                count("select count(*) from notifications where send_status = 'SENT' and template_code = 'REMINDER_D1'"),
                count("select count(*) from chat_messages where message_type = 'CONTENT'"),
                count("select count(*) from applications where payment_status = 'REFUNDED'"),
                count("select count(distinct application_id) from match_placement_logs where decision = 'REALLOCATED'"),
                count("select count(*) from exception_cases where case_status <> 'RESOLVED'"),
                count("select count(*) from exception_cases where case_status <> 'RESOLVED' and case_type = 'VENUE_UNAVAILABLE'"),
                count("select count(*) from exception_cases where case_status <> 'RESOLVED' and case_type = 'NO_SHOW_CANDIDATE'"),
                count("select count(distinct user_id) from applications"),
                count("select count(*) from applications where payment_status = 'PAID'"));
        return new Progress(status.now(), status.offsetSeconds(), status.jumps(), demoAppId,
                demoApp == null ? null : demoApp[1], demoApp == null ? null : demoApp[2],
                demoApp == null ? null : demoApp[3], feedback, avoid,
                userHas("user14", "a.match_status = 'ALTERNATIVE_OFFERED' or a.match_status = 'TRANSFERRED'"),
                userHas("user14", "a.transferred_from_id is not null"),
                userHas("user23", "a.match_status = 'ALTERNATIVE_OFFERED' or a.payment_status = 'REFUNDED'"),
                userHas("user23", "a.payment_status = 'REFUNDED'"),
                userHas("user25", "true"), stats);
    }

    private boolean userHas(String loginId, String condition) {
        return exists("select 1 from applications a join users u on u.id = a.user_id where u.login_id = ? and ("
                + condition + ")", loginId);
    }

    private boolean exists(String sql, Object... args) {
        return !jdbc.queryForList(sql + " limit 1", args).isEmpty();
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }
}
