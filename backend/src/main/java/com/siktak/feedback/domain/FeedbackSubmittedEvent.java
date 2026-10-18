package com.siktak.feedback.domain;

/** 만족도 피드백이 저장된 직후 (FDB-02). 다음 일정 추천 알림(RET-02) 등이 듣는다. */
public record FeedbackSubmittedEvent(Long userId, Long applicationId) {
}
