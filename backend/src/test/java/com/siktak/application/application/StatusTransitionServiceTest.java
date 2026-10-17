package com.siktak.application.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.siktak.application.domain.Application;
import com.siktak.application.domain.PassType;
import com.siktak.application.domain.status.ActorType;
import com.siktak.application.domain.status.ApplicationStatus;
import com.siktak.application.domain.status.MatchStatus;
import com.siktak.application.domain.status.PaymentStatus;
import com.siktak.application.domain.status.StatusAxis;
import com.siktak.application.domain.status.StatusHistory;
import com.siktak.application.domain.status.Transition;
import com.siktak.application.domain.status.TransitionContext;
import com.siktak.application.infra.ApplicationRepository;
import com.siktak.application.infra.StatusHistoryRepository;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;

@ExtendWith(MockitoExtension.class)
class StatusTransitionServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private StatusHistoryRepository statusHistoryRepository;

    private StatusTransitionService service;

    private final TransitionContext participant = TransitionContext.of(ActorType.PARTICIPANT, 7L, "신청 제출");
    private final TransitionContext system = TransitionContext.system("자동");

    @BeforeEach
    void setUp() {
        service = new StatusTransitionService(applicationRepository, statusHistoryRepository);
    }

    @Test
    void 새_신청에_A1을_적용하면_DRAFT가_되고_저장된_뒤_이력이_남는다() {
        Application application = Application.newApplication(7L, PassType.SINGLE);
        given(applicationRepository.save(any())).willAnswer(inv -> {
            Application saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });

        boolean changed = service.apply(application, Transition.A1, participant);

        assertThat(changed).isTrue();
        assertThat(application.getApplicationStatus()).isEqualTo(ApplicationStatus.DRAFT);
        StatusHistory history = captureHistory();
        assertThat(history.getTargetId()).isEqualTo(100L);
        assertThat(history.getAxis()).isEqualTo(StatusAxis.APPLICATION);
        assertThat(history.getFromStatus()).isNull();
        assertThat(history.getToStatus()).isEqualTo("DRAFT");
        assertThat(history.getTransitionCode()).isEqualTo("A1");
        assertThat(history.getActorType()).isEqualTo(ActorType.PARTICIPANT);
        assertThat(history.getActorId()).isEqualTo(7L);
        assertThat(history.getReason()).isEqualTo("신청 제출");
    }

    @Test
    void 허용된_전이는_상태를_바꾸고_이전_값과_새_값을_기록한다() {
        Application application = saved(ApplicationStatus.SUBMITTED, PaymentStatus.PENDING, null);

        service.apply(application, Transition.P2, TransitionContext.of(ActorType.PAYMENT_GATEWAY, null, "승인"));

        assertThat(application.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        StatusHistory history = captureHistory();
        assertThat(history.getFromStatus()).isEqualTo("PENDING");
        assertThat(history.getToStatus()).isEqualTo("PAID");
        assertThat(history.getTransitionCode()).isEqualTo("P2");
    }

    @Test
    void 전이표에_없는_전이는_INVALID_STATE_TRANSITION으로_거부하고_상태와_이력을_남기지_않는다() {
        Application application = saved(ApplicationStatus.SUBMITTED, PaymentStatus.PENDING, null);

        // 매칭 상태가 아직 없는데(결제 대기) M3(MATCHING→CONFIRMED)를 요청
        assertThatThrownBy(() -> service.apply(application, Transition.M3, system))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);

        assertThat(application.getMatchStatus()).isNull();
        verify(statusHistoryRepository, never()).save(any());
    }

    @Test
    void 환불_완료처럼_종료된_상태에서는_다시_결제_완료로_갈_수_없다() {
        Application application = saved(ApplicationStatus.SUBMITTED, PaymentStatus.REFUNDED, MatchStatus.NO_MATCH);

        assertThatThrownBy(() -> service.apply(application, Transition.P7,
                TransitionContext.of(ActorType.PARTICIPANT, 7L, "이동")))
                .isInstanceOf(BusinessException.class);
        assertThat(application.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void 이미_목표_상태면_상태를_바꾸지_않고_no_op_이력만_남긴다() {
        Application application = saved(ApplicationStatus.SUBMITTED, PaymentStatus.PAID, MatchStatus.WAITING);

        boolean changed = service.apply(application, Transition.M1, system);

        assertThat(changed).isFalse();
        assertThat(application.getMatchStatus()).isEqualTo(MatchStatus.WAITING);
        StatusHistory history = captureHistory();
        assertThat(history.getFromStatus()).isEqualTo("WAITING");
        assertThat(history.getToStatus()).isEqualTo("WAITING");
        assertThat(history.getReason()).startsWith(StatusTransitionService.NO_OP_PREFIX);
    }

    @Test
    void 전이표의_주체와_다른_주체가_요청하면_거부한다() {
        Application application = saved(ApplicationStatus.SUBMITTED, PaymentStatus.PENDING, null);

        assertThatThrownBy(() -> service.apply(application, Transition.P2, participant))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(application.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void 다음_상태가_여러_개인_M12는_목표를_지정해야_하고_허용된_목표만_받는다() {
        Application application = saved(ApplicationStatus.SUBMITTED, PaymentStatus.PAID, MatchStatus.EXCEPTION);
        TransitionContext operator = TransitionContext.of(ActorType.OPERATOR, 1L, "예외 처리");

        assertThatThrownBy(() -> service.apply(application, Transition.M12, operator))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.apply(application, Transition.M12, MatchStatus.NO_MATCH, operator))
                .isInstanceOf(IllegalArgumentException.class);

        service.apply(application, Transition.M12, MatchStatus.CONFIRMED, operator);
        assertThat(application.getMatchStatus()).isEqualTo(MatchStatus.CONFIRMED);
    }

    @Test
    void 매칭_실행_ID가_있으면_이력에_함께_남긴다() {
        Application application = saved(ApplicationStatus.SUBMITTED, PaymentStatus.PAID, MatchStatus.WAITING);

        service.apply(application, Transition.M2, new TransitionContext(ActorType.SCHEDULER, null, "마감", 55L));

        assertThat(captureHistory().getMatchRunId()).isEqualTo(55L);
    }

    @Test
    void 전이표는_states_md의_전이를_모두_갖는다() {
        assertThat(Transition.of(StatusAxis.APPLICATION)).hasSize(5);
        assertThat(Transition.of(StatusAxis.PAYMENT)).hasSize(15); // P7b 포함 (6.1)
        assertThat(Transition.of(StatusAxis.MATCH)).hasSize(14);
        assertThat(Transition.of(StatusAxis.ATTENDANCE)).hasSize(5);
        assertThat(Transition.M12.getToStatuses())
                .containsExactlyInAnyOrderElementsOf(List.of(MatchStatus.WAITING, MatchStatus.CONFIRMED,
                        MatchStatus.ALTERNATIVE_OFFERED));
    }

    private Application saved(ApplicationStatus a, PaymentStatus p, MatchStatus m) {
        Application application = Application.newApplication(7L, PassType.SINGLE);
        ReflectionTestUtils.setField(application, "id", 1L);
        ReflectionTestUtils.setField(application, "applicationStatus", a);
        ReflectionTestUtils.setField(application, "paymentStatus", p);
        ReflectionTestUtils.setField(application, "matchStatus", m);
        return application;
    }

    private StatusHistory captureHistory() {
        ArgumentCaptor<StatusHistory> captor = ArgumentCaptor.forClass(StatusHistory.class);
        verify(statusHistoryRepository).save(captor.capture());
        return captor.getValue();
    }
}
