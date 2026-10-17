package com.siktak.application.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.application.domain.Application;
import com.siktak.application.domain.PassType;
import com.siktak.application.domain.status.ActorType;
import com.siktak.application.domain.status.ApplicationStatus;
import com.siktak.application.domain.status.MatchStatus;
import com.siktak.application.domain.status.PaymentStatus;
import com.siktak.application.domain.status.Transition;
import com.siktak.application.domain.status.TransitionContext;
import com.siktak.application.infra.ApplicationRepository;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.payment.application.PaymentService;
import com.siktak.payment.domain.Payment;
import com.siktak.payment.domain.PaymentPurpose;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.infra.EventSlotRepository;

import lombok.RequiredArgsConstructor;

/** 신청 제출과 결제 연결 (APP-02·03, PAY-01). 의존 방향: application → payment (역방향 없음). */
@Service
@RequiredArgsConstructor
public class ApplicationService {

    /** states.md 9장 policy.payment-deadline-hours 기본값. 설정값(settings) 연동은 이후 작업. */
    static final Duration PAYMENT_DEADLINE = Duration.ofHours(24);

    private static final Set<MatchStatus> FINISHED_MATCH = Set.of(MatchStatus.TRANSFERRED, MatchStatus.NO_MATCH);

    private final ApplicationRepository applicationRepository;
    private final EventSlotRepository eventSlotRepository;
    private final StatusTransitionService transitionService;
    private final PaymentService paymentService;
    private final Clock clock;

    /** 신청 제출: A1 → A2, 1회권이면 결제 대기 P1. */
    @Transactional
    public Application submit(Long userId, List<Long> slotIdsInPriorityOrder, PassType passType) {
        if (passType != PassType.SINGLE) {
            // 멤버십·크레딧(P1a)은 3.x 이용권 작업에서 구현한다.
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "현재는 1회권만 신청할 수 있습니다.");
        }
        List<Long> slotIds = List.copyOf(new LinkedHashSet<>(slotIdsInPriorityOrder));
        if (slotIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "회차를 하나 이상 선택해야 합니다.");
        }
        Instant now = Instant.now(clock);
        List<EventSlot> slots = eventSlotRepository.findByIdIn(slotIds);
        if (slots.size() != slotIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "선택한 회차를 찾을 수 없습니다.");
        }
        if (slots.stream().anyMatch(slot -> !slot.isOpenForApplication(now))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "모집이 끝난 회차가 포함되어 있습니다.");
        }
        rejectDuplicate(userId, slotIds);

        TransitionContext participant = TransitionContext.of(ActorType.PARTICIPANT, userId, "신청 제출");
        Application application = Application.newApplication(userId, passType);
        application.chooseSlots(slotIds);
        transitionService.apply(application, Transition.A1, participant);
        transitionService.apply(application, Transition.A2, participant);
        application.markSubmitted(now);

        Payment payment = paymentService.prepare(userId, PaymentPurpose.SINGLE_TICKET, "app-" + application.getId());
        Instant earliestMatchRun = slots.stream().map(EventSlot::getMatchRunAt).min(Comparator.naturalOrder())
                .orElseThrow();
        Instant byDeadline = now.plus(PAYMENT_DEADLINE);
        application.linkPayment(payment.getId(),
                byDeadline.isBefore(earliestMatchRun) ? byDeadline : earliestMatchRun);
        transitionService.apply(application, Transition.P1, TransitionContext.system("1회권 결제 대기"));
        return application;
    }

    /**
     * 모의 결제 승인: P2 → M1. 이미 결제 완료면 아무것도 바꾸지 않는다 (멱등).
     * PG 승인 결과는 PAYMENT_GATEWAY 주체로 기록한다.
     */
    @Transactional
    public Application approvePayment(Long userId, Long applicationId) {
        Application application = getOwned(userId, applicationId);
        if (application.getApplicationStatus() != ApplicationStatus.SUBMITTED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "제출된 신청만 결제할 수 있습니다.");
        }
        if (application.getPaymentStatus() == PaymentStatus.PAID) {
            return application;
        }
        boolean approved = paymentService.approve(application.getPaymentId());
        TransitionContext gateway = TransitionContext.of(ActorType.PAYMENT_GATEWAY, null, "모의 결제");
        if (!approved) {
            transitionService.apply(application, Transition.P3, gateway);
            throw new BusinessException(ErrorCode.PAYMENT_FAILED);
        }
        transitionService.apply(application, Transition.P2, gateway);
        transitionService.apply(application, Transition.M1, TransitionContext.system("결제 완료, 매칭 대기"));
        return application;
    }

    @Transactional(readOnly = true)
    public List<Application> findMine(Long userId) {
        return applicationRepository.findByUserIdOrderByIdDesc(userId);
    }

    @Transactional(readOnly = true)
    public Application getOwned(Long userId, Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "신청을 찾을 수 없습니다."));
        if (!application.getUserId().equals(userId)) {
            // 다른 사용자의 신청은 존재 여부도 알리지 않는다
            throw new BusinessException(ErrorCode.NOT_FOUND, "신청을 찾을 수 없습니다.");
        }
        return application;
    }

    /** 같은 회차에 진행 중인 신청이 있으면 거절한다 (states.md 1장: 같은 회차 중복 신청 불가). */
    private void rejectDuplicate(Long userId, List<Long> slotIds) {
        boolean duplicate = applicationRepository.findByUserIdOrderByIdDesc(userId).stream()
                .filter(a -> a.getApplicationStatus() == ApplicationStatus.SUBMITTED)
                .filter(a -> a.getMatchStatus() == null || !FINISHED_MATCH.contains(a.getMatchStatus()))
                .anyMatch(a -> slotIds.stream().anyMatch(a::hasChosenSlot));
        if (duplicate) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 신청한 회차가 포함되어 있습니다.");
        }
    }
}
