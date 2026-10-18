package com.siktak.feedback.domain;

import java.util.List;

/** 회차 행사 종료 처리가 끝났다 (EVENT_END). 피드백 요청 알림 등 후속 처리가 듣는다. attendedUserIds는 체크인한 참가자. */
public record EventEndedEvent(Long eventSlotId, List<Long> attendedUserIds, List<Long> noShowCandidateUserIds) {
}
