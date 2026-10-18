package com.siktak.notification.application;

import java.util.List;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.siktak.feedback.application.HistoryService;
import com.siktak.feedback.application.HistoryService.Recommendation;
import com.siktak.feedback.domain.FeedbackSubmittedEvent;

import lombok.RequiredArgsConstructor;

/** 피드백을 낸 참가자에게 다음 일정 추천(최대 3개)을 알린다 (RET-02, PRD 14장). 신청당 1건, 추천할 회차가 없으면 보내지 않는다. */
@Component
@RequiredArgsConstructor
public class NextSlotRecommendListener {

    private final NotificationService notificationService;
    private final HistoryService historyService;

    @EventListener
    public void on(FeedbackSubmittedEvent event) {
        List<Recommendation> recommendations = historyService.recommendations(event.userId());
        if (recommendations.isEmpty()) {
            return;
        }
        notificationService.enqueue(event.userId(), "NEXT_SLOT_RECOMMEND", Map.of(),
                Map.of("slotIds", recommendations.stream().map(Recommendation::eventSlotId).toList(), "link", "/app"),
                "NEXT_SLOT_RECOMMEND:application-" + event.applicationId());
    }
}
