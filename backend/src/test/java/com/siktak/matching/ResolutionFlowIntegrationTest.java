package com.siktak.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

import com.siktak.application.application.RefundRetryJobHandler;
import com.siktak.application.application.RefundService;
import com.siktak.application.domain.Application;
import com.siktak.application.domain.status.MatchStatus;
import com.siktak.application.domain.status.PaymentStatus;
import com.siktak.application.infra.ApplicationRepository;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.common.schedule.JobTrigger;
import com.siktak.common.schedule.ScheduledJob;
import com.siktak.common.schedule.ScheduledJobRegistry;
import com.siktak.common.schedule.ScheduledJobRunner;
import com.siktak.exception.domain.CaseStatus;
import com.siktak.exception.domain.CaseType;
import com.siktak.exception.domain.ExceptionCase;
import com.siktak.matching.application.ResolutionService;
import com.siktak.matching.application.ResolutionService.ResolutionView;
import com.siktak.matching.application.ResolutionTimeoutJobHandler;
import com.siktak.matching.domain.MatchResolution;
import com.siktak.matching.domain.ResolutionChoice;
import com.siktak.matching.domain.ResolutionStatus;
import com.siktak.payment.domain.Payment;
import com.siktak.payment.domain.TxStatus;
import com.siktak.payment.domain.UserPassType;
import com.siktak.payment.infra.MockPaymentGateway;
import com.siktak.payment.infra.PaymentRepository;

/**
 * 6.1 매칭 실패 해결: demo-scenario J2 뒤 user14 일정 이동, user23 환불, user24 미응답 보관,
 * 환불 실패 주입 시 예외 전환과 운영자 처리, 중복 환불 방지.
 */
@Tag("db")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class ResolutionFlowIntegrationTest extends DemoScenarioDbTestSupport {

    @Autowired private ResolutionService resolutionService;
    @Autowired private RefundService refundService;
    @Autowired private RefundRetryJobHandler refundRetryJobHandler;
    @Autowired private ResolutionTimeoutJobHandler timeoutJobHandler;
    @Autowired private ScheduledJobRegistry jobRegistry;
    @Autowired private ScheduledJobRunner jobRunner;
    @Autowired private MockPaymentGateway mockGateway;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ApplicationRepository applicationRepository;

    @AfterEach
    void resetGateway() {
        mockGateway.setFailCancel(false);
        jobRegistry.replaceAll(List.of());
    }

    @Test
    void J2_뒤_user14는_S5로_이동_user23은_자동환불_user24는_미응답으로_보관된다() {
        runJ2();

        // ---- user14 TRANSFER → S5 (M7, D3): 결제는 1건 그대로, 새 신청이 같은 결제로 PAID·WAITING
        Application u14 = app.get(14);
        ResolutionView before = resolutionService.view(u14.getUserId(), u14.getId());
        assertThat(before.offeredSlots()).extracting(o -> o.slot().getId()).containsExactly(s4.getId(), s5.getId());
        assertThat(before.offeredSlots()).extracting(ResolutionService.OfferedSlot::reasonCode)
                .containsExactly("CHOSEN_REGION", "OTHER_REGION");
        assertThat(before.choices()).allSatisfy(c -> assertThat(c.available()).isTrue());

        ResolutionView moved = resolutionService.choose(u14.getUserId(), u14.getId(), ResolutionChoice.TRANSFER,
                s5.getId());
        assertThat(u14.getMatchStatus()).isEqualTo(MatchStatus.TRANSFERRED);
        assertThat(u14.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(transitionCodes(14)).endsWith("P5", "P7", "M7");
        MatchResolution r14 = moved.resolution();
        assertThat(r14.getResolutionStatus()).isEqualTo(ResolutionStatus.RESOLVED);
        assertThat(r14.getChosenSlotId()).isEqualTo(s5.getId());

        Application newApp = applicationRepository.findById(r14.getNewApplicationId()).orElseThrow();
        assertThat(newApp.getTransferredFromId()).isEqualTo(u14.getId());
        assertThat(newApp.getPaymentId()).isEqualTo(u14.getPaymentId());
        assertThat(newApp.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(newApp.getMatchStatus()).isEqualTo(MatchStatus.WAITING);
        assertThat(newApp.getSlotChoicesByPriority()).singleElement()
                .satisfies(c -> assertThat(c.getEventSlotId()).isEqualTo(s5.getId()));
        assertThat(paymentCount(u14.getUserId())).isEqualTo(1);
        // 같은 선택을 다시 보내도 그대로 (멱등), 다른 선택은 409
        assertThat(resolutionService.choose(u14.getUserId(), u14.getId(), ResolutionChoice.TRANSFER, s5.getId())
                .resolution().getNewApplicationId()).isEqualTo(newApp.getId());
        assertThatThrownBy(() -> resolutionService.choose(u14.getUserId(), u14.getId(), ResolutionChoice.REFUND, null))
                .isInstanceOf(BusinessException.class);

        // ---- 모집 중이 아닌 회차로는 이동할 수 없다 (409)
        Application u23 = app.get(23);
        s4.markMatched();
        assertThatThrownBy(() -> resolutionService.choose(u23.getUserId(), u23.getId(), ResolutionChoice.TRANSFER,
                s4.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));

        // ---- user23 REFUND: P6 → M8 → P8 → P9, 운영자 개입 없음
        ResolutionView refunded = resolutionService.choose(u23.getUserId(), u23.getId(), ResolutionChoice.REFUND,
                null);
        assertThat(u23.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(u23.getMatchStatus()).isEqualTo(MatchStatus.NO_MATCH);
        assertThat(refunded.resolution().getChoice()).isEqualTo("REFUND");
        assertThat(transitionCodes(23)).endsWith("P6", "M8", "P8", "P9");
        Payment p23 = paymentRepository.findById(u23.getPaymentId()).orElseThrow();
        assertThat(p23.getTxStatus()).isEqualTo(TxStatus.CANCELED);
        assertThat(p23.getRefundKey()).isEqualTo("refund-pay-" + p23.getId());
        // 중복 환불 방지: 다시 처리·다시 선택해도 PG 취소는 1번
        refundService.process(u23.getId());
        resolutionService.choose(u23.getUserId(), u23.getId(), ResolutionChoice.REFUND, null);
        assertThat(mockGateway.cancelCallCount(p23.getRefundKey())).isEqualTo(1);
        assertThat(openCases(CaseType.REFUND_FAILED)).isEmpty();

        // ---- user24 미응답: respond_by(S3 시작) 전에는 그대로, 지나면 작업 실행기가 보관 처리 (M8a + P7b)
        Application u24 = app.get(24);
        MatchResolution r24 = resolutionRepository.findByApplicationId(u24.getId()).orElseThrow();
        assertThat(r24.getRespondBy()).isEqualTo(s3.getStartsAt());
        List<ScheduledJob> pending = timeoutJobHandler.loadPending();
        assertThat(pending).contains(new ScheduledJob(ResolutionService.TIMEOUT_JOB, r24.getId(), s3.getStartsAt()));
        jobRegistry.replaceAll(pending);

        clock.set(s3.getStartsAt().minusSeconds(60));
        jobRunner.runDue(JobTrigger.tick());
        assertThat(u24.getMatchStatus()).isEqualTo(MatchStatus.ALTERNATIVE_OFFERED);

        clock.set(s3.getStartsAt());
        jobRunner.runDue(JobTrigger.tick());
        assertThat(u24.getMatchStatus()).isEqualTo(MatchStatus.NO_MATCH);
        assertThat(u24.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(r24.getResolutionStatus()).isEqualTo(ResolutionStatus.AUTO_KEPT);
        assertThat(transitionCodes(24)).endsWith("P5", "P7b", "M8a");
        assertThat(credits(u24.getUserId())).isEqualTo(1);
        // 다시 실행돼도 크레딧은 1개 (멱등)
        resolutionService.autoKeep(r24.getId(), JobTrigger.catchUp());
        assertThat(credits(u24.getUserId())).isEqualTo(1);
        // 기한이 지나면 참가자 선택은 받지 않는다
        assertThatThrownBy(() -> resolutionService.choose(u24.getUserId(), u24.getId(), ResolutionChoice.REFUND,
                null)).isInstanceOf(BusinessException.class);
    }

    @Test
    void 환불_실패를_주입하면_재시도_뒤_REFUND_FAILED와_예외_1건_운영자가_재처리하거나_수동완료한다() {
        runJ2();
        mockGateway.setFailCancel(true);

        // user23: 첫 시도 실패 → 처리 중 유지, 재시도 3번도 실패 → P10 + 예외
        Application u23 = app.get(23);
        resolutionService.choose(u23.getUserId(), u23.getId(), ResolutionChoice.REFUND, null);
        assertThat(u23.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_PROCESSING);
        assertThat(refundRetryJobHandler.loadPending()).extracting(ScheduledJob::targetId).contains(u23.getId());
        for (int i = 0; i < 3; i++) {
            clock.set(clock.instant().plus(Duration.ofMinutes(1)));
            refundRetryJobHandler.execute(u23.getId(), JobTrigger.tick());
        }
        assertThat(u23.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_FAILED);
        assertThat(transitionCodes(23)).endsWith("P6", "M8", "P8", "P10");
        Payment p23 = paymentRepository.findById(u23.getPaymentId()).orElseThrow();
        assertThat(p23.getRetryCount()).isEqualTo(4);
        assertThat(p23.getTxStatus()).isEqualTo(TxStatus.CANCEL_FAILED);
        assertThat(mockGateway.cancelCallCount(p23.getRefundKey())).isEqualTo(4);
        List<ExceptionCase> cases = openCases(CaseType.REFUND_FAILED);
        assertThat(cases).singleElement().satisfies(c -> assertThat(c.getApplicationId()).isEqualTo(u23.getId()));
        // 실패 확정 뒤에는 재시도 작업이 와도 PG를 부르지 않는다
        refundRetryJobHandler.execute(u23.getId(), JobTrigger.tick());
        assertThat(mockGateway.cancelCallCount(p23.getRefundKey())).isEqualTo(4);

        // 운영자 재처리 (P11): 아직 실패면 다시 REFUND_FAILED, 예외는 열린 채
        refundService.retryByOperator(u23.getId(), operatorId);
        assertThat(u23.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_FAILED);
        assertThat(openCases(CaseType.REFUND_FAILED)).hasSize(1);
        mockGateway.setFailCancel(false);
        refundService.retryByOperator(u23.getId(), operatorId);
        assertThat(u23.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(transitionCodes(23)).endsWith("P11", "P10", "P11", "P9");
        assertThat(exceptionCaseRepository.findById(cases.get(0).getId()).orElseThrow().getCaseStatus())
                .isEqualTo(CaseStatus.RESOLVED);

        // user24: 실패 확정 뒤 운영자 수동 완료 (P12, 사유 필수)
        mockGateway.setFailCancel(true);
        Application u24 = app.get(24);
        resolutionService.choose(u24.getUserId(), u24.getId(), ResolutionChoice.REFUND, null);
        for (int i = 0; i < 3; i++) {
            refundRetryJobHandler.execute(u24.getId(), JobTrigger.tick());
        }
        assertThat(u24.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_FAILED);
        assertThatThrownBy(() -> refundService.markRefundedByOperator(u24.getId(), operatorId, " "))
                .isInstanceOf(BusinessException.class);
        refundService.markRefundedByOperator(u24.getId(), operatorId, "계좌 이체로 환불 완료");
        assertThat(u24.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(transitionCodes(24)).endsWith("P10", "P12");
        assertThat(openCases(CaseType.REFUND_FAILED)).isEmpty();
    }

    // ------------------------------------------------------------------ 도우미

    private void runJ2() {
        clock.set(s1.getMatchRunAt());
        matchingService.runForSlot(s1.getId(), null);
        clock.set(s3.getMatchRunAt());
        matchingService.runForSlot(s3.getId(), null);
        for (int no : new int[] {14, 23, 24}) {
            assertThat(app.get(no).getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_AVAILABLE);
        }
    }

    private int paymentCount(Long userId) {
        return jdbc.queryForObject("select count(*) from payments where user_id = ?", Integer.class, userId);
    }

    private int credits(Long userId) {
        return jdbc.queryForObject("select count(*) from user_passes where user_id = ? and pass_type = ?",
                Integer.class, userId, UserPassType.SINGLE_CREDIT.name());
    }
}
