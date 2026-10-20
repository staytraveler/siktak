package com.siktak.notification.application;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.siktak.feedback.domain.EventEndedEvent;

import lombok.RequiredArgsConstructor;

/** 행사 종료 처리 뒤 체크인한 참가자에게 만족도·안전 피드백을 요청한다 (PRD 14장 "행사 종료 후"). 회차·참가자당 1건. */
@Component
@RequiredArgsConstructor
public class FeedbackRequestListener {

    private final NotificationService notificationService;
    private final com.siktak.common.schedule.ScheduledJobRegistry jobRegistry;
    private final java.time.Clock clock;

    @EventListener
    public void on(EventEndedEvent event) {
        // 하루 뒤에 아직 피드백을 안 낸 참석자에게 한 번만 다시 알린다 (PRD 15장 "피드백 미응답")
        jobRegistry.schedule(new com.siktak.common.schedule.ScheduledJob(FeedbackReminderJobHandler.JOB_TYPE,
                event.eventSlotId(), java.time.Instant.now(clock).plus(FeedbackReminderJobHandler.DELAY)));
        for (Long userId : event.attendedUserIds()) {
            notificationService.enqueue(userId, "FEEDBACK_REQUEST", Map.of(),
                    Map.of("slotId", event.eventSlotId(), "link", "/app"),
                    "FEEDBACK_REQUEST:slot-" + event.eventSlotId() + ":user-" + userId);
        }
    }
}
