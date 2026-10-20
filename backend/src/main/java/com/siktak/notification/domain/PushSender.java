package com.siktak.notification.domain;

/**
 * 앱 푸시 발송 포트 (INF-01·07, CLAUDE.md 8장). 구현은 {@code siktak.notification.push.provider}로 고른다.
 * 푸시 실패는 인앱 알림 발송을 막지 않는다 — 실패하면 예외를 던지고, 서비스가 푸시 결과만 FAILED로 기록한다.
 */
public interface PushSender {
    void push(Notification notification);
}
