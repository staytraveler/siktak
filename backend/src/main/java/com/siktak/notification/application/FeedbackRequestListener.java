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

    @EventListener
    public void on(EventEndedEvent event) {
        for (Long userId : event.attendedUserIds()) {
            notificationService.enqueue(userId, "FEEDBACK_REQUEST", Map.of(),
                    Map.of("slotId", event.eventSlotId(), "link", "/app"),
                    "FEEDBACK_REQUEST:slot-" + event.eventSlotId() + ":user-" + userId);
        }
    }
}
