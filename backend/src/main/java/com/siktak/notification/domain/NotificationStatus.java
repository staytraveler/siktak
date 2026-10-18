package com.siktak.notification.domain;

/** 알림 발송 상태 (erd.md notifications.send_status). */
public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED,
    CANCELED
}
