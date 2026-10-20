package com.siktak.admin.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.notification.domain.NotificationTemplate;
import com.siktak.notification.infra.NotificationTemplateRepository;

import lombok.RequiredArgsConstructor;

/**
 * 운영자 화면용 조회·관리 (PRD 11장): 결제·환불·이용권 사용 내역, 자동 메시지 템플릿과 발송 이력.
 * 조회는 읽기 전용 SQL로 한곳에서 모아 보여준다.
 */
@Service
@RequiredArgsConstructor
public class AdminOpsService {

    private final JdbcTemplate jdbc;
    private final NotificationTemplateRepository templateRepository;

    // ------------------------------------------------------------------ 결제·환불 (PAY-01·03)

    /** 신청 건별 결제·환불 상태. 최근 신청 순. */
    @Transactional(readOnly = true)
    public List<PaymentRow> payments(String paymentStatus) {
        return jdbc.query("""
                select a.id as application_id, u.login_id, u.name, a.pass_type, a.application_status, a.payment_status,
                       a.match_status, p.amount, p.tx_status, p.last_error, a.user_pass_id, a.submitted_at
                from applications a
                join users u on u.id = a.user_id
                left join payments p on p.id = a.payment_id
                where (? is null or a.payment_status = ?)
                order by a.id desc
                limit 500
                """, (rs, i) -> new PaymentRow(rs.getLong("application_id"), rs.getString("login_id"),
                        rs.getString("name"), rs.getString("pass_type"), rs.getString("application_status"),
                        rs.getString("payment_status"), rs.getString("match_status"),
                        (Long) rs.getObject("amount"), rs.getString("tx_status"), rs.getString("last_error"),
                        (Long) rs.getObject("user_pass_id"), instant(rs.getTimestamp("submitted_at"))),
                paymentStatus, paymentStatus);
    }

    /** 멤버십·보관 크레딧(이용권) 사용 내역. */
    @Transactional(readOnly = true)
    public List<PassRow> passes() {
        return jdbc.query("""
                select up.id, u.login_id, u.name, up.pass_type, up.pass_status, up.starts_at, up.expires_at,
                       up.source_application_id, up.used_application_id
                from user_passes up join users u on u.id = up.user_id
                order by up.id desc
                limit 500
                """, (rs, i) -> new PassRow(rs.getLong("id"), rs.getString("login_id"), rs.getString("name"),
                rs.getString("pass_type"), rs.getString("pass_status"), instant(rs.getTimestamp("starts_at")),
                instant(rs.getTimestamp("expires_at")), (Long) rs.getObject("source_application_id"),
                (Long) rs.getObject("used_application_id")));
    }

    // ------------------------------------------------------------------ 자동 메시지 관리

    @Transactional(readOnly = true)
    public List<TemplateRow> templates() {
        return templateRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(NotificationTemplate::getCode))
                .map(t -> new TemplateRow(t.getCode(), t.getTitleTemplate(), t.getBodyTemplate(), t.isActive()))
                .toList();
    }

    /** 제목·본문·사용 여부를 고친다. 끄면 이 알림은 더 만들어지지 않는다. 본문 변수({{x}})는 그대로 둔다. */
    @Transactional
    public TemplateRow updateTemplate(String code, String title, String body, boolean active) {
        if (title == null || title.isBlank() || body == null || body.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "제목과 본문을 입력해 주세요.");
        }
        NotificationTemplate t = templateRepository.findById(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "알림 템플릿을 찾을 수 없습니다."));
        t.update(title.trim(), body.trim(), active);
        return new TemplateRow(t.getCode(), t.getTitleTemplate(), t.getBodyTemplate(), t.isActive());
    }

    /** 자동 발송 이력 (인앱 알림 + 푸시 결과). 최근순. */
    @Transactional(readOnly = true)
    public List<HistoryRow> history(String sendStatus) {
        return jdbc.query("""
                select n.id, u.login_id, u.name, n.template_code, n.channel, n.send_status, n.attempts, n.last_error,
                       n.push_status, n.scheduled_at, n.sent_at
                from notifications n join users u on u.id = n.user_id
                where (? is null or n.send_status = ?)
                order by n.id desc
                limit 300
                """, (rs, i) -> new HistoryRow(rs.getLong("id"), rs.getString("login_id"), rs.getString("name"),
                rs.getString("template_code"), rs.getString("channel"), rs.getString("send_status"),
                rs.getInt("attempts"), rs.getString("last_error"), rs.getString("push_status"),
                instant(rs.getTimestamp("scheduled_at")), instant(rs.getTimestamp("sent_at"))),
                sendStatus, sendStatus);
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    public record PaymentRow(Long applicationId, String loginId, String name, String passType,
            String applicationStatus, String paymentStatus, String matchStatus, Long amount, String txStatus,
            String lastError, Long userPassId, Instant submittedAt) {
    }

    public record PassRow(Long id, String loginId, String name, String passType, String passStatus, Instant startsAt,
            Instant expiresAt, Long sourceApplicationId, Long usedApplicationId) {
    }

    public record TemplateRow(String code, String title, String body, boolean active) {
    }

    public record HistoryRow(Long id, String loginId, String name, String templateCode, String channel,
            String sendStatus, int attempts, String lastError, String pushStatus, Instant scheduledAt,
            Instant sentAt) {
    }
}
