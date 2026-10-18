package com.siktak.notification.domain;

/**
 * 알림 발송 포트 (CLAUDE.md 8장). 구현은 {@code siktak.notification.channel}로 고른다.
 * 실패하면 예외를 던진다 — 재시도·예외 전환은 {@code NotificationService}가 맡는다.
 */
public interface NotificationSender {

    void send(Notification notification);
}
