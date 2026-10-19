package com.siktak.notification.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.common.schedule.ScheduledJob;
import com.siktak.common.schedule.ScheduledJobRegistry;
import com.siktak.exception.application.ExceptionCaseService;
import com.siktak.exception.domain.CaseType;
import com.siktak.notification.domain.Notification;
import com.siktak.notification.domain.NotificationSender;
import com.siktak.notification.domain.NotificationStatus;
import com.siktak.notification.domain.NotificationTemplate;
import com.siktak.notification.infra.NotificationRepository;
import com.siktak.notification.infra.NotificationTemplateRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 알림 저장 → 발송 (INF-01·07·08). 저장 후 시각이 됐으면 바로, 아니면 {@code NOTIFICATION_SEND} 작업으로 예약 발송한다.
 * 발송이 실패하면 1분 뒤 다시 시도하고, 재시도 횟수를 넘기면 FAILED + 예외 처리함(NOTIFICATION_FAILED)으로 넘긴다.
 * 같은 {@code dedupKey}는 한 번만 만든다 (멱등).
 */
@Slf4j
@Service
public class NotificationService {

    public static final String SEND_JOB = "NOTIFICATION_SEND";
    static final Duration RETRY_DELAY = Duration.ofMinutes(1);

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationSender sender;
    private final ExceptionCaseService exceptionCaseService;
    private final ScheduledJobRegistry jobRegistry;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final int retryMax;

    public NotificationService(NotificationRepository notificationRepository,
            NotificationTemplateRepository templateRepository, NotificationSender sender,
            ExceptionCaseService exceptionCaseService, ScheduledJobRegistry jobRegistry, JdbcTemplate jdbc, Clock clock,
            @Value("${siktak.policy.notification-retry-max:3}") int retryMax) {
        this.notificationRepository = notificationRepository;
        this.templateRepository = templateRepository;
        this.sender = sender;
        this.exceptionCaseService = exceptionCaseService;
        this.jobRegistry = jobRegistry;
        this.jdbc = jdbc;
        this.clock = clock;
        this.retryMax = retryMax;
    }

    /** 즉시 발송. */
    @Transactional
    public Notification enqueue(Long userId, String templateCode, Map<String, String> vars, Map<String, Object> payload,
            String dedupKey) {
        return enqueue(userId, templateCode, vars, payload, Instant.now(clock), dedupKey);
    }

    /** @param scheduledAt 이 시각이 지났으면 바로, 아니면 그 시각에 발송 */
    @Transactional
    public Notification enqueue(Long userId, String templateCode, Map<String, String> vars, Map<String, Object> payload,
            Instant scheduledAt, String dedupKey) {
        // 같은 키의 동시 요청이 확인-저장 사이에 끼어 유니크 제약 위반(500)이 나지 않게, 키 단위로 트랜잭션 끝까지 직렬화한다.
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtext(?))::text", String.class, "notification:" + dedupKey);
        Optional<Notification> existing = notificationRepository.findByDedupKey(dedupKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        NotificationTemplate template = templateRepository.findById(templateCode)
                .filter(NotificationTemplate::isActive)
                .orElseThrow(() -> new IllegalStateException("알림 템플릿이 없거나 꺼져 있다: " + templateCode));
        Notification n = notificationRepository.save(
                Notification.pending(userId, template, vars, payload, scheduledAt, dedupKey));
        if (!scheduledAt.isAfter(Instant.now(clock))) {
            attemptSend(n);
        } else {
            jobRegistry.schedule(new ScheduledJob(SEND_JOB, n.getId(), scheduledAt));
        }
        return n;
    }

    @Transactional(readOnly = true)
    public boolean exists(String dedupKey) {
        return notificationRepository.findByDedupKey(dedupKey).isPresent();
    }

    /** 작업 실행기가 부른다. 이미 보냈거나 취소된 알림이면 아무것도 하지 않는다 (멱등). */
    @Transactional
    public void send(Long notificationId) {
        notificationRepository.findById(notificationId).ifPresent(this::attemptSend);
    }

    private void attemptSend(Notification n) {
        if (n.getSendStatus() != NotificationStatus.PENDING) {
            return;
        }
        Instant now = Instant.now(clock);
        try {
            sender.send(n);
            n.markSent(now);
        } catch (RuntimeException e) {
            n.markAttemptFailed(String.valueOf(e.getMessage()));
            if (n.getAttempts() > retryMax) {
                n.markFailed();
                exceptionCaseService.open(CaseType.NOTIFICATION_FAILED, failedKey(n.getId()), null, null,
                        "알림 발송이 " + n.getAttempts() + "번 실패했습니다: " + n.getTemplateCode(),
                        Map.of("notificationId", n.getId(), "userId", n.getUserId(), "lastError", n.getLastError()));
                log.warn("Notification {} failed permanently: {}", n.getId(), n.getLastError());
            } else {
                jobRegistry.schedule(new ScheduledJob(SEND_JOB, n.getId(), now.plus(RETRY_DELAY)));
            }
        }
    }

    /** 기동 시 적재: 아직 안 나간 알림. 예약 시각이 지난 것은 바로 실행 대상이 된다 (catch-up). */
    @Transactional(readOnly = true)
    public List<ScheduledJob> pendingJobs() {
        return notificationRepository.findBySendStatusOrderByIdAsc(NotificationStatus.PENDING).stream()
                .map(n -> new ScheduledJob(SEND_JOB, n.getId(), n.getScheduledAt())).toList();
    }

    /** 참가자 알림함: 발송된 알림만, 최신순. */
    @Transactional(readOnly = true)
    public List<Notification> inbox(Long userId) {
        return notificationRepository.findByUserIdAndSendStatusOrderByIdDesc(userId, NotificationStatus.SENT);
    }

    @Transactional
    public Notification markRead(Long userId, Long notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "알림을 찾을 수 없습니다."));
        n.markRead(Instant.now(clock));
        return n;
    }

    /** 운영자 재발송 (NOTIFICATION_FAILED 처리). 성공하면 해당 예외를 해결한다. 이미 보낸 알림이면 아무것도 하지 않는다. */
    @Transactional
    public Notification resendByOperator(Long notificationId, Long operatorId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "알림을 찾을 수 없습니다."));
        if (n.getSendStatus() == NotificationStatus.SENT) {
            return n;
        }
        n.resetForResend();
        attemptSend(n);
        if (n.getSendStatus() == NotificationStatus.SENT) {
            exceptionCaseService.resolve(failedKey(notificationId), operatorId, "운영자 재발송 성공");
        }
        return n;
    }

    public static String failedKey(Long notificationId) {
        return "NOTIFICATION_FAILED:notification-" + notificationId;
    }
}
