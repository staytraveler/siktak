package com.siktak.application.application;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.application.domain.Application;
import com.siktak.application.domain.status.ActorType;
import com.siktak.application.domain.status.PaymentStatus;
import com.siktak.application.domain.status.Transition;
import com.siktak.application.domain.status.TransitionContext;
import com.siktak.application.infra.ApplicationRepository;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.common.schedule.ScheduledJob;
import com.siktak.common.schedule.ScheduledJobRegistry;
import com.siktak.exception.application.ExceptionCaseService;
import com.siktak.exception.domain.CaseType;
import com.siktak.payment.application.PaymentService;
import com.siktak.payment.application.PaymentService.CancelOutcome;

/**
 * 1회권 자동 환불 (PRD PAY-02·03, STA-05, states.md P8~P12).
 * <ul>
 *   <li>{@code REFUND_REQUESTED} → P8 → PG 취소 → 성공하면 P9 {@code REFUNDED}</li>
 *   <li>실패하면 작업 실행기로 1분 뒤 재시도. 실패가 {@code policy.refund-retry-max}회를 넘으면
 *       P10 {@code REFUND_FAILED} + {@code ExceptionCase(REFUND_FAILED)}</li>
 *   <li>운영자 재처리 P11, 수동 완료 P12</li>
 * </ul>
 * 중복 환불 방지: 결제 1건에 환불 키 1개(payments.refund_key 유니크), 결제 행을 잠근 뒤 PG 호출,
 * 이미 취소된 거래면 PG를 다시 부르지 않는다.
 */
@Service
public class RefundService {

    public static final String RETRY_JOB = "REFUND_RETRY";
    static final Duration RETRY_DELAY = Duration.ofMinutes(1);

    private final ApplicationRepository applicationRepository;
    private final StatusTransitionService transitionService;
    private final PaymentService paymentService;
    private final ExceptionCaseService exceptionCaseService;
    private final ScheduledJobRegistry jobRegistry;
    private final Clock clock;
    private final int retryMax;

    public RefundService(ApplicationRepository applicationRepository, StatusTransitionService transitionService,
            PaymentService paymentService, ExceptionCaseService exceptionCaseService,
            ScheduledJobRegistry jobRegistry, Clock clock,
            @Value("${siktak.policy.refund-retry-max:3}") int retryMax) {
        this.applicationRepository = applicationRepository;
        this.transitionService = transitionService;
        this.paymentService = paymentService;
        this.exceptionCaseService = exceptionCaseService;
        this.jobRegistry = jobRegistry;
        this.clock = clock;
        this.retryMax = retryMax;
    }

    /**
     * 환불을 진행한다. 환불 선택(P6) 직후와 재시도 작업에서 부른다.
     * 이미 끝났거나({@code REFUNDED}) 운영자 확인 대기({@code REFUND_FAILED})면 아무것도 하지 않는다 (멱등).
     */
    @Transactional
    public PaymentStatus process(Long applicationId) {
        Application application = load(applicationId);
        PaymentStatus status = application.getPaymentStatus();
        if (status == PaymentStatus.REFUNDED || status == PaymentStatus.REFUND_FAILED) {
            return status;
        }
        if (status == PaymentStatus.REFUND_REQUESTED) {
            transitionService.apply(application, Transition.P8, TransitionContext.system("자동 환불 처리 시작"));
        }
        if (application.getPaymentStatus() != PaymentStatus.REFUND_PROCESSING) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "환불 처리 중인 신청이 아닙니다.");
        }
        CancelOutcome outcome = paymentService.cancel(application.getPaymentId());
        if (outcome.canceled()) {
            complete(application, "PG 취소 승인");
        } else if (outcome.failureCount() <= retryMax) {
            // [해석] 재시도 횟수 = 첫 시도 뒤 다시 부르는 횟수. 기본 3이면 PG를 최대 4번 부른다
            jobRegistry.schedule(new ScheduledJob(RETRY_JOB, applicationId, clock.instant().plus(RETRY_DELAY)));
        } else {
            transitionService.apply(application, Transition.P10,
                    TransitionContext.system("자동 환불 " + outcome.failureCount() + "회 실패: " + outcome.error()));
            paymentService.markCancelFailed(application.getPaymentId());
            openRefundFailedCase(application, outcome);
        }
        return application.getPaymentStatus();
    }

    /**
     * 운영자 재처리 (P11). PG를 한 번 다시 부르고, 실패하면 다시 {@code REFUND_FAILED}로 둔다 (예외는 열린 채 유지).
     * [해석] 운영자 재처리는 자동 재시도 없이 1회만 시도한다.
     */
    @Transactional
    public PaymentStatus retryByOperator(Long applicationId, Long operatorId) {
        Application application = load(applicationId);
        if (application.getPaymentStatus() == PaymentStatus.REFUNDED) {
            return PaymentStatus.REFUNDED;
        }
        transitionService.apply(application, Transition.P11,
                TransitionContext.of(ActorType.OPERATOR, operatorId, "운영자 환불 재처리"));
        CancelOutcome outcome = paymentService.cancel(application.getPaymentId());
        if (outcome.canceled()) {
            complete(application, "운영자 재처리로 PG 취소 승인");
            exceptionCaseService.resolve(ExceptionCaseService.refundFailedKey(applicationId), operatorId,
                    "운영자 재처리로 환불 완료");
        } else {
            transitionService.apply(application, Transition.P10,
                    TransitionContext.system("운영자 재처리 실패: " + outcome.error()));
            paymentService.markCancelFailed(application.getPaymentId());
        }
        return application.getPaymentStatus();
    }

    /**
     * 운영자가 PG 밖에서 환불을 마쳤다 (P12, 사유 필수).
     * [해석] payments.tx_status는 PG 거래 사실이므로 CANCEL_FAILED 그대로 둔다.
     */
    @Transactional
    public PaymentStatus markRefundedByOperator(Long applicationId, Long operatorId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "수동 환불 완료 사유를 입력해 주세요.");
        }
        Application application = load(applicationId);
        transitionService.apply(application, Transition.P12,
                TransitionContext.of(ActorType.OPERATOR, operatorId, "수동 환불 완료: " + reason));
        exceptionCaseService.resolve(ExceptionCaseService.refundFailedKey(applicationId), operatorId, reason);
        return application.getPaymentStatus();
    }

    private void complete(Application application, String reason) {
        transitionService.apply(application, Transition.P9,
                TransitionContext.of(ActorType.PAYMENT_GATEWAY, null, reason));
        jobRegistry.cancel(RETRY_JOB, application.getId());
    }

    private void openRefundFailedCase(Application application, CancelOutcome outcome) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("paymentId", application.getPaymentId());
        detail.put("failureCount", outcome.failureCount());
        detail.put("lastError", outcome.error());
        exceptionCaseService.open(CaseType.REFUND_FAILED, ExceptionCaseService.refundFailedKey(application.getId()),
                application.getId(), null,
                "자동 환불이 " + outcome.failureCount() + "회 실패했습니다. 재처리 또는 수동 환불 완료가 필요합니다.",
                detail);
    }

    private Application load(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "신청을 찾을 수 없습니다."));
    }
}
