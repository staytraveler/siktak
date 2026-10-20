package com.siktak.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

import com.siktak.application.domain.Application;
import com.siktak.common.schedule.JobTrigger;
import com.siktak.common.schedule.ScheduledJobRegistry;
import com.siktak.feedback.application.AttendanceService;
import com.siktak.feedback.application.FeedbackService;
import com.siktak.notification.application.FeedbackReminderJobHandler;
import com.siktak.notification.domain.Notification;
import com.siktak.notification.domain.NotificationStatus;
import com.siktak.notification.infra.NotificationRepository;
import com.siktak.slot.api.CreateSlotRequest;
import com.siktak.slot.application.SlotService;

/**
 * PRD 15장 "피드백 미응답 1회 리마인드"와 PRD 14장 "다음 모집 시작 알림".
 * 피드백을 낸 사람은 리마인드를 받지 않고, 안 낸 참석자는 한 번만 받으며, 새 회차가 열리면 이전 참석자에게 한 번 알린다.
 */
@Tag("db")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class ReminderAndNewSlotIntegrationTest extends DemoScenarioDbTestSupport {

    @Autowired private AttendanceService attendanceService;
    @Autowired private FeedbackService feedbackService;
    @Autowired private FeedbackReminderJobHandler reminderHandler;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private SlotService slotService;
    @Autowired private ScheduledJobRegistry jobRegistry;

    @AfterEach
    void clearJobs() {
        jobRegistry.replaceAll(List.of());
    }

    private long count(Application a, String template) {
        return notificationRepository.findByUserIdAndSendStatusOrderByIdDesc(a.getUserId(), NotificationStatus.SENT)
                .stream().filter((Notification n) -> n.getTemplateCode().equals(template)).count();
    }

    @Test
    void 피드백을_안_낸_참석자에게만_한_번_리마인드하고_새_회차가_열리면_이전_참석자에게_알린다() {
        clock.set(s1.getMatchRunAt());
        matchingService.runForSlot(s1.getId(), null);
        // user01·user02 체크인, user02만 피드백
        clock.set(s1.getStartsAt());
        Application u1 = app.get(1);
        Application u2 = app.get(2);
        attendanceService.checkIn(u1.getUserId(), u1.getId());
        attendanceService.checkIn(u2.getUserId(), u2.getId());
        clock.set(s1.getEndsAt());
        attendanceService.processEventEnd(s1.getId());
        feedbackService.submit(u2.getUserId(), u2.getId(), 5, 4, 3, 4, 9, "좋았어요");

        // 종료 하루 뒤: 피드백을 안 낸 user01만 리마인드, user02는 받지 않는다
        clock.set(s1.getEndsAt().plus(FeedbackReminderJobHandler.DELAY));
        reminderHandler.execute(s1.getId(), JobTrigger.tick());
        assertThat(count(u1, "FEEDBACK_REMINDER")).isEqualTo(1);
        assertThat(count(u2, "FEEDBACK_REMINDER")).isZero();
        // 체크인하지 않은 사람(user13)에게도 가지 않는다
        assertThat(count(app.get(13), "FEEDBACK_REMINDER")).isZero();

        // 다시 실행해도 한 번만 (멱등)
        reminderHandler.execute(s1.getId(), JobTrigger.tick());
        assertThat(count(u1, "FEEDBACK_REMINDER")).isEqualTo(1);

        // 행사 종료 시 하루 뒤 리마인드 작업이 예약돼 있다
        assertThat(reminderHandler.loadPending()).anyMatch(j -> j.targetId() == s1.getId()
                && j.dueAt().equals(s1.getEndsAt().plus(FeedbackReminderJobHandler.DELAY)));

        // 새 회차(같은 지역) 개설 → 이전 참석자(user01·user02)에게 다음 모집 시작 알림, 두 번 만들어지지 않는다
        long before1 = count(u1, "NEXT_SLOT_RECOMMEND");
        var startsAt = clock.instant().plus(Duration.ofDays(10));
        slotService.create(new CreateSlotRequest(s1.getRegion().getId(), startsAt, startsAt.plus(Duration.ofHours(2)), 30,
                startsAt.minus(Duration.ofDays(3)), startsAt.minus(Duration.ofDays(3))));
        assertThat(count(u1, "NEXT_SLOT_RECOMMEND")).isEqualTo(before1 + 1);
        // 참석하지 않은 사람(user13, 노쇼 후보)에게는 가지 않는다
        assertThat(count(app.get(13), "NEXT_SLOT_RECOMMEND")).isZero();
    }
}
