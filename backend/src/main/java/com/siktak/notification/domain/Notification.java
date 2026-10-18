package com.siktak.notification.domain;

import java.time.Instant;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.siktak.common.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 참가자 알림 1건 (INF-01·07·08). {@code dedup_key} 유니크로 같은 알림이 두 번 나가지 않는다. */
@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    public static final String CHANNEL_IN_APP = "IN_APP";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "template_code", nullable = false)
    private String templateCode;

    @Column(nullable = false)
    private String channel;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> payload;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "send_status", nullable = false)
    private NotificationStatus sendStatus;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "dedup_key", nullable = false, unique = true)
    private String dedupKey;

    public static Notification pending(Long userId, NotificationTemplate template, Map<String, String> vars,
            Map<String, Object> payload, Instant scheduledAt, String dedupKey) {
        Notification n = new Notification();
        n.userId = userId;
        n.templateCode = template.getCode();
        n.channel = CHANNEL_IN_APP;
        n.title = template.renderTitle(vars);
        n.body = template.renderBody(vars);
        n.payload = payload;
        n.scheduledAt = scheduledAt;
        n.sendStatus = NotificationStatus.PENDING;
        n.dedupKey = dedupKey;
        return n;
    }

    public void markSent(Instant now) {
        attempts++;
        sendStatus = NotificationStatus.SENT;
        sentAt = now;
        lastError = null;
    }

    /** 실패를 기록한다. 시도 횟수를 하나 올린다. */
    public void markAttemptFailed(String error) {
        attempts++;
        lastError = error;
    }

    public void markFailed() {
        sendStatus = NotificationStatus.FAILED;
    }

    /** 운영자 재발송: 실패 상태를 PENDING으로 되돌려 다시 시도할 수 있게 한다. */
    public void resetForResend() {
        sendStatus = NotificationStatus.PENDING;
        attempts = 0;
        lastError = null;
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }
}
