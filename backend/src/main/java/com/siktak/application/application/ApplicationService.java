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
import com.siktak.exception.application.ExceptionCaseService;
import com.siktak.payment.application.UserPassService;
import com.siktak.payment.domain.Payment;
import com.siktak.payment.domain.PaymentPurpose;
import com.siktak.payment.domain.UserPass;
import com.siktak.profile.application.ProfileService;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.infra.EventSlotRepository;

import lombok.RequiredArgsConstructor;

/** 신청 제출과 결제 연결 (APP-02·03, PAY-01). 의존 방향: application → payment (역방향 없음). */
@Service
@RequiredArgsConstructor
public class ApplicationService {

    /** states.md 9장 policy.payment-deadline-hours 기본값. 설정값(settings) 연동은 이후 작업. */
    static final Duration PAYMENT_DEADLINE = Duration.ofHours(24);

    /** APP-02 복수 일정 선택 상한 (지시서 3.2: 회차 1~5개). */
    static final int MAX_SLOT_CHOICES = 5;

    private static final Set<MatchStatus> FINISHED_MATCH = Set.of(MatchStatus.TRANSFERRED, MatchStatus.NO_MATCH);

    private final ApplicationRepository applicationRepository;
    private final EventSlotRepository eventSlotRepository;
    private final StatusTransitionService transitionService;
    private final PaymentService paymentService;
    private final UserPassService userPassService;
    private final ExceptionCaseService exceptionCaseService;
    private final ProfileService profileService;
    private final Clock clock;

    /** 신청 제출: A1 → A2, 1회권이면 결제 대기 P1. */
    @Transactional
    public Application submit(Long userId, List<Long> slotIdsInPriorityOrder, PassType passType) {
        List<Long> slotIds = List.copyOf(new LinkedHashSet<>(slotIdsInPriorityOrder));
        if (slotIds.isEmpty() || slotIds.size() > MAX_SLOT_CHOICES) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "회차는 1~" + MAX_SLOT_CHOICES + "개까지 선택할 수 있습니다.");
        }
        if (!profileService.isComplete(userId)) {
            // 화면은 이 코드를 받으면 프로필 입력 화면으로 안내한다 (APP-02)
            throw new BusinessException(ErrorCode.PROFILE_INCOMPLETE);
        }
        Instant now = Instant.now(clock);
        List<EventSlot> slots = eventSlotRepository.findByIdIn(slotIds);
        if (slots.size() != slotIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "선택한 회차를 찾을 수 없습니다.");
        }
        if (slots.stream().anyMatch(slot -> !slot.isOpenForApplication(now))) {
            throw new BusinessException(ErrorCode.CONFLICT, "모집이 끝난 회차가 포함되어 있습니다.");
        }
        rejectDuplicate(userId, slotIds);

        TransitionContext participant = TransitionContext.of(ActorType.PARTICIPANT, userId, "신청 제출");
        Application application = Application.newApplication(userId, passType);
        application.chooseSlots(slotIds);
        transitionService.apply(application, Transition.A1, participant);
        transitionService.apply(application, Transition.A2, participant);
        application.markSubmitted(now);

        if (passType == PassType.MEMBERSHIP) {
            // P1a: 유효한 멤버십이면 결제 없이 바로 PAID → 매칭 대기. 기간 안의 회차만 신청할 수 있다 (횟수 제한 없음, D1)
            UserPass membership = userPassService.activeMembership(userId, now)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "이용 중인 멤버십이 없어요."));
            if (slots.stream().anyMatch(slot -> !slot.getStartsAt().isBefore(membership.getExpiresAt()))) {
                throw new BusinessException(ErrorCode.CONFLICT, "멤버십 기간이 끝난 뒤의 일정이 포함되어 있어요.");
            }
            application.linkCredit(membership.getId());
            transitionService.apply(application, Transition.P1a, TransitionContext.system("멤버십 이용"));
            transitionService.apply(application, Transition.M1, TransitionContext.system("멤버십 이용, 매칭 대기"));
            return application;
        }
        if (passType == PassType.CREDIT) {
            // P1a: 보관 이용권 1장을 소진하고 결제 없이 바로 PAID → 매칭 대기 (states.md P1a)
            UserPass credit = userPassService.consumeCredit(userId, application.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "사용할 수 있는 보관 이용권이 없습니다."));
            application.linkCredit(credit.getId());
            transitionService.apply(application, Transition.P1a, TransitionContext.system("보관 이용권 사용"));
            transitionService.apply(application, Transition.M1, TransitionContext.system("이용권 사용, 매칭 대기"));
            return application;
        }

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
        if (application.getPaymentStatus() == PaymentStatus.FAILED) {
            // P4: 결제 기한 이내에만 다시 결제할 수 있다 (states.md)
            Instant due = application.getPaymentDueAt();
            if (due != null && Instant.now(clock).isAfter(due)) {
                throw new BusinessException(ErrorCode.CONFLICT, "결제 기한이 지났어요.");
            }
            transitionService.apply(application, Transition.P4,
                    TransitionContext.of(ActorType.PARTICIPANT, userId, "재결제 시도"));
        }
        boolean approved = paymentService.approve(application.getPaymentId());
        TransitionContext gateway = TransitionContext.of(ActorType.PAYMENT_GATEWAY, null, "모의 결제");
        String caseKey = "PAYMENT_ERROR:application-" + application.getId();
        if (!approved) {
            transitionService.apply(application, Transition.P3, gateway);
            // 예외를 던지면 트랜잭션이 롤백돼 실패 이력·예외가 사라지므로, 실패 상태를 저장하고 그대로 돌려준다.
            // 참가자는 FAILED 상태에서 다시 결제할 수 있고, 반복되는 오류는 운영자 예외 처리함(MAT-13)에서 본다.
            exceptionCaseService.open(com.siktak.exception.domain.CaseType.PAYMENT_ERROR, caseKey,
                    application.getId(), null, "결제 승인 실패: 참가자가 다시 결제하거나 운영자가 확인해야 합니다.",
                    java.util.Map.of("paymentId", application.getPaymentId()));
            return application;
        }
        exceptionCaseService.resolve(caseKey, null, "재결제로 해결됨");
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
