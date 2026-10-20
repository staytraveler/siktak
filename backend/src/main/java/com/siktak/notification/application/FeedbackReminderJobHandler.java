package com.siktak.notification.application;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.siktak.common.schedule.JobTrigger;
import com.siktak.common.schedule.ScheduledJob;
import com.siktak.common.schedule.ScheduledJobHandler;

import lombok.RequiredArgsConstructor;

/**
 * 피드백 미응답 1회 리마인드 (PRD 15장 "피드백 미응답", states.md 7장). 행사 종료 {@link #DELAY} 뒤에 한 번 실행해,
 * 체크인했지만 아직 피드백을 내지 않은 참가자에게만 알림을 보낸다. 신청당 1건(dedup)이라 몇 번 실행돼도 한 번만 나가고,
 * 다음 신청을 막지는 않는다. 대상 ID는 회차 ID.
 */
@Component
@RequiredArgsConstructor
public class FeedbackReminderJobHandler implements ScheduledJobHandler {

    public static final String JOB_TYPE = "FEEDBACK_REMIND";
    public static final Duration DELAY = Duration.ofDays(1);

    private final JdbcTemplate jdbc;
    private final NotificationService notificationService;

    @Override
    public String jobType() {
        return JOB_TYPE;
    }

    /** 기동 시 catch-up: 종료 처리된 회차마다 (종료 + 1일)에 한 번. 이미 보낸 알림은 dedup으로 건너뛴다. */
    @Override
    public List<ScheduledJob> loadPending() {
        return jdbc.query("select id, ends_at from event_slots where slot_status = 'DONE'",
                (rs, i) -> new ScheduledJob(JOB_TYPE, rs.getLong("id"), ((Timestamp) rs.getTimestamp("ends_at")).toInstant().plus(DELAY)));
    }

    @Override
    public void execute(long slotId, JobTrigger trigger) {
        List<long[]> targets = jdbc.query("""
                select att.user_id, att.application_id
                from attendances att join dining_tables t on t.id = att.dining_table_id
                where t.event_slot_id = ? and att.attendance_status = 'ATTENDED'
                  and not exists (select 1 from feedbacks f where f.application_id = att.application_id)
                """, (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)}, slotId);
        for (long[] t : targets) {
            notificationService.enqueue(t[0], "FEEDBACK_REMINDER", Map.of(),
                    Map.of("applicationId", t[1], "link", "/app/applications/" + t[1]),
                    "FEEDBACK_REMINDER:app-" + t[1]);
        }
    }
}
