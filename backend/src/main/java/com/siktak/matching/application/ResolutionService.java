package com.siktak.matching.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.siktak.application.application.RefundService;
import com.siktak.application.application.StatusTransitionService;
import com.siktak.application.domain.Application;
import com.siktak.application.domain.ApplicationSlotChoice;
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
import com.siktak.common.schedule.JobTrigger;
import com.siktak.common.schedule.ScheduledJob;
import com.siktak.common.schedule.ScheduledJobRegistry;
import com.siktak.matching.domain.MatchResolution;
import com.siktak.matching.domain.ResolutionChoice;
import com.siktak.matching.domain.ResolutionStatus;
import com.siktak.matching.domain.alternative.AlternativeSlot;
import com.siktak.matching.infra.MatchResolutionRepository;
import com.siktak.payment.application.UserPassService;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.infra.EventSlotRepository;

/**
 * 매칭 실패 후 참가자 선택 처리 (PRD STA-04~06, PAY-02·03, states.md M7·M8·M8a, D2·D3·D6).
 * <ul>
 *   <li>TRANSFER: 기존 신청 {@code TRANSFERRED} 종료 + 새 신청(같은 결제, {@code PAID}) → M1</li>
 *   <li>KEEP: {@code NO_MATCH} + 1회권이면 크레딧 발급</li>
 *   <li>REFUND: {@code NO_MATCH} + P6 → 자동 환불({@link RefundService})</li>
 *   <li>응답 기한({@code respond_by})까지 미응답이면 작업 실행기가 KEEP과 같게 처리 ({@code AUTO_KEPT})</li>
 * </ul>
 */
@Service
public class ResolutionService {

    public static final String TIMEOUT_JOB = "RESOLUTION_TIMEOUT";

    private static final Set<MatchStatus> FINISHED_MATCH = Set.of(MatchStatus.TRANSFERRED, MatchStatus.NO_MATCH);

    private final ApplicationRepository applicationRepository;
    private final MatchResolutionRepository resolutionRepository;
    private final EventSlotRepository eventSlotRepository;
    private final StatusTransitionService transitionService;
    private final UserPassService userPassService;
    private final RefundService refundService;
    private final ScheduledJobRegistry jobRegistry;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ResolutionService(ApplicationRepository applicationRepository,
            MatchResolutionRepository resolutionRepository, EventSlotRepository eventSlotRepository,
            StatusTransitionService transitionService, UserPassService userPassService, RefundService refundService,
            ScheduledJobRegistry jobRegistry, PlatformTransactionManager transactionManager, Clock clock) {
        this.applicationRepository = applicationRepository;
        this.resolutionRepository = resolutionRepository;
        this.eventSlotRepository = eventSlotRepository;
        this.transitionService = transitionService;
        this.userPassService = userPassService;
        this.refundService = refundService;
        this.jobRegistry = jobRegistry;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** 응답 기한 작업 등록. 대체 일정을 제시할 때(M6) 부른다. 커밋 뒤에 반영된다. */
    public void scheduleTimeout(MatchResolution resolution) {
        jobRegistry.schedule(new ScheduledJob(TIMEOUT_JOB, resolution.getId(), resolution.getRespondBy()));
    }

    // ------------------------------------------------------------------ 조회

    /** 실패 안내: 추천 회차(이유), 선택지 3개, 응답 기한. 본인 신청만. */
    @Transactional(readOnly = true)
    public ResolutionView view(Long userId, Long applicationId) {
        Application application = loadOwned(userId, applicationId);
        MatchResolution resolution = loadResolution(applicationId);
        Instant now = clock.instant();
        boolean open = resolution.isOffered() && now.isBefore(resolution.getRespondBy());

        List<Long> chosenSlotIds = application.getSlotChoicesByPriority().stream()
                .map(ApplicationSlotChoice::getEventSlotId).toList();
        List<Long> slotIds = new ArrayList<>(chosenSlotIds);
        slotIds.addAll(resolution.getOfferedSlotIds());
        Map<Long, EventSlot> slots = eventSlotRepository.findByIdIn(slotIds).stream()
                .collect(Collectors.toMap(EventSlot::getId, Function.identity()));
        Set<Long> chosenRegions = chosenSlotIds.stream().map(slots::get).filter(s -> s != null)
                .map(s -> s.getRegion().getId()).collect(Collectors.toSet());

        List<OfferedSlot> offered = resolution.getOfferedSlotIds().stream()
                .map(slots::get).filter(s -> s != null)
                .map(s -> {
                    AlternativeSlot.Reason reason = chosenRegions.contains(s.getRegion().getId())
                            ? AlternativeSlot.Reason.CHOSEN_REGION : AlternativeSlot.Reason.OTHER_REGION;
                    return new OfferedSlot(s, reason.name(), reason.text(), open && s.isOpenForApplication(now));
                })
                .toList();
        boolean canTransfer = offered.stream().anyMatch(OfferedSlot::available);
        boolean canRefund = open && application.getPaymentStatus() == PaymentStatus.REFUND_AVAILABLE;
        List<ChoiceOption> choices = List.of(
                new ChoiceOption(ResolutionChoice.TRANSFER, canTransfer, "추천 일정으로 옮겨요. 결제는 그대로 유지돼요."),
                new ChoiceOption(ResolutionChoice.KEEP, open, keepDescription(application.getPassType())),
                new ChoiceOption(ResolutionChoice.REFUND, canRefund, canRefund || application.getPassType() == PassType.SINGLE
                        ? "결제를 취소하고 환불해요." : "1회권 결제만 환불할 수 있어요."));
        return new ResolutionView(application, resolution, offered, choices);
    }

    // ------------------------------------------------------------------ 선택

    /**
     * 참가자 선택을 처리한다. 같은 선택을 다시 보내면 아무것도 바꾸지 않는다 (멱등).
     * 동시에 두 요청이 오면 하나만 반영되고 나머지는 409.
     */
    public ResolutionView choose(Long userId, Long applicationId, ResolutionChoice choice, Long slotId) {
        Boolean changed;
        try {
            changed = tx.execute(status -> chooseInTx(userId, applicationId, choice, slotId));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "다른 요청이 먼저 처리되었습니다. 상태를 다시 확인해 주세요.");
        }
        if (Boolean.TRUE.equals(changed) && choice == ResolutionChoice.REFUND) {
            // P6 직후 바로 P8 (states.md P6 "즉시 P8"). 선택 트랜잭션이 커밋된 뒤에 PG를 부른다
            refundService.process(applicationId);
        }
        return tx.execute(status -> view(userId, applicationId));
    }

    private boolean chooseInTx(Long userId, Long applicationId, ResolutionChoice choice, Long slotId) {
        Application application = loadOwned(userId, applicationId);
        MatchResolution resolution = loadResolution(applicationId);
        if (!resolution.isOffered()) {
            boolean same = resolution.getChoiceValue() == choice
                    && (choice != ResolutionChoice.TRANSFER || resolution.getChosenSlotId().equals(slotId));
            if (same) {
                return false;
            }
            throw new BusinessException(ErrorCode.CONFLICT, "이미 선택을 마쳤습니다.");
        }
        Instant now = clock.instant();
        if (!now.isBefore(resolution.getRespondBy())) {
            throw new BusinessException(ErrorCode.CONFLICT, "응답 기한이 지났습니다. 이용권은 보관 처리됩니다.");
        }
        if (application.getMatchStatus() != MatchStatus.ALTERNATIVE_OFFERED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "대체 일정 선택 대기 중인 신청이 아닙니다.");
        }
        TransitionContext participant = TransitionContext.of(ActorType.PARTICIPANT, userId, choiceReason(choice));
        switch (choice) {
            case TRANSFER -> {
                Application moved = transfer(application, resolution, slotId, participant, now);
                resolution.resolve(choice, slotId, moved.getId(), now);
            }
            case KEEP -> {
                keep(application, participant, Transition.P7, Transition.M8);
                resolution.resolve(choice, null, null, now);
            }
            case REFUND -> {
                if (application.getPaymentStatus() != PaymentStatus.REFUND_AVAILABLE) {
                    throw new BusinessException(ErrorCode.CONFLICT, "환불할 수 있는 결제가 아닙니다.");
                }
                // 불변 조건(states.md 6장): REFUND_AVAILABLE이면 ALTERNATIVE_OFFERED — 결제를 먼저 옮긴다
                transitionService.apply(application, Transition.P6, participant);
                transitionService.apply(application, Transition.M8, participant);
                resolution.resolve(choice, null, null, now);
            }
        }
        jobRegistry.cancel(TIMEOUT_JOB, resolution.getId());
        return true;
    }

    /** M7 (states.md D3): 기존 신청 종료, 같은 결제를 쓰는 새 신청 → M1. 새 결제는 만들지 않는다. */
    private Application transfer(Application application, MatchResolution resolution, Long slotId,
            TransitionContext participant, Instant now) {
        if (slotId == null || !resolution.getOfferedSlotIds().contains(slotId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "추천된 일정 중에서 골라 주세요.");
        }
        EventSlot slot = eventSlotRepository.findById(slotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "회차를 찾을 수 없습니다."));
        if (!slot.isOpenForApplication(now)) {
            throw new BusinessException(ErrorCode.CONFLICT, "모집이 끝난 회차입니다. 다른 선택지를 골라 주세요.");
        }
        boolean duplicate = applicationRepository.findByUserIdOrderByIdDesc(application.getUserId()).stream()
                .filter(a -> !a.getId().equals(application.getId()))
                .filter(a -> a.getApplicationStatus() == ApplicationStatus.SUBMITTED)
                .filter(a -> a.getMatchStatus() == null || !FINISHED_MATCH.contains(a.getMatchStatus()))
                .anyMatch(a -> a.hasChosenSlot(slotId));
        if (duplicate) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 신청한 회차입니다.");
        }

        if (application.getPaymentStatus() == PaymentStatus.REFUND_AVAILABLE) {
            transitionService.apply(application, Transition.P7, participant);
        }
        transitionService.apply(application, Transition.M7, participant);

        Application moved = Application.transferOf(application, slotId);
        TransitionContext movedBy = TransitionContext.of(ActorType.PARTICIPANT, application.getUserId(),
                "일정 이동 (신청 #" + application.getId() + "에서)");
        transitionService.apply(moved, Transition.A1, movedBy);
        transitionService.apply(moved, Transition.A2, movedBy);
        moved.markSubmitted(now);
        // 새 결제 없이 기존 결제를 공유하므로 결제 대기(P1)를 거치지 않고 바로 PAID (states.md M7 연쇄)
        transitionService.apply(moved, Transition.P1a,
                TransitionContext.system("일정 이동: 기존 결제 #" + application.getPaymentId() + " 유지"));
        transitionService.apply(moved, Transition.M1, TransitionContext.system("일정 이동, 매칭 대기"));
        return moved;
    }

    /** 보관 (M8 / M8a): 결제를 PAID로 되돌리고(1회권) 매칭 종료, 1회권·크레딧이면 크레딧 발급. */
    private void keep(Application application, TransitionContext context, Transition paymentBack,
            Transition matchEnd) {
        if (application.getPaymentStatus() == PaymentStatus.REFUND_AVAILABLE) {
            transitionService.apply(application, paymentBack, context);
        }
        transitionService.apply(application, matchEnd, context);
        // [해석] 크레딧으로 낸 신청의 "크레딧 복원"(states.md 8장)은 새 크레딧 발급으로 처리한다
        if (application.getPassType() == PassType.SINGLE || application.getPassType() == PassType.CREDIT) {
            userPassService.issueCredit(application.getUserId(), application.getId());
        }
    }

    // ------------------------------------------------------------------ 미응답

    /** 응답 기한 경과 (M8a, D6): KEEP과 같게 처리하고 {@code AUTO_KEPT}. 이미 처리됐으면 아무것도 하지 않는다. */
    @Transactional
    public void autoKeep(long resolutionId, JobTrigger trigger) {
        MatchResolution resolution = resolutionRepository.findById(resolutionId).orElse(null);
        if (resolution == null || !resolution.isOffered()) {
            return;
        }
        Application application = applicationRepository.findById(resolution.getApplicationId()).orElseThrow();
        if (application.getMatchStatus() != MatchStatus.ALTERNATIVE_OFFERED) {
            return;
        }
        TransitionContext scheduler = new TransitionContext(ActorType.SCHEDULER, trigger.triggeredBy(),
                "대체 일정 미응답, 이용권 보관 처리 (" + trigger.reason() + ")", null);
        keep(application, scheduler, Transition.P7b, Transition.M8a);
        resolution.autoKeep();
    }

    /** 기동 시 적재할 미응답 작업 (작업 실행기 catch-up). */
    @Transactional(readOnly = true)
    public List<ScheduledJob> pendingTimeouts() {
        return resolutionRepository.findByResolutionStatusOrderByIdAsc(ResolutionStatus.OFFERED).stream()
                .map(r -> new ScheduledJob(TIMEOUT_JOB, r.getId(), r.getRespondBy()))
                .toList();
    }

    // ------------------------------------------------------------------ 도우미

    private Application loadOwned(Long userId, Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "신청을 찾을 수 없습니다."));
        if (!application.getUserId().equals(userId)) {
            // 다른 사용자의 신청은 존재 여부도 알리지 않는다
            throw new BusinessException(ErrorCode.NOT_FOUND, "신청을 찾을 수 없습니다.");
        }
        return application;
    }

    private MatchResolution loadResolution(Long applicationId) {
        return resolutionRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "대체 일정 안내가 없는 신청입니다."));
    }

    private static String keepDescription(PassType passType) {
        return switch (passType) {
            case SINGLE -> "1회 이용권으로 보관해 다음 신청에 쓸 수 있어요.";
            case CREDIT -> "사용한 크레딧을 돌려드려요.";
            case MEMBERSHIP -> "멤버십은 그대로 유지돼요. 다음 일정에 다시 신청해 주세요.";
        };
    }

    private static String choiceReason(ResolutionChoice choice) {
        return switch (choice) {
            case TRANSFER -> "대체 일정으로 이동 선택";
            case KEEP -> "이용권 보관 선택";
            case REFUND -> "환불 선택";
        };
    }

    // ------------------------------------------------------------------ 결과

    public record OfferedSlot(EventSlot slot, String reasonCode, String reason, boolean available) {
    }

    public record ChoiceOption(ResolutionChoice choice, boolean available, String description) {
    }

    public record ResolutionView(Application application, MatchResolution resolution, List<OfferedSlot> offeredSlots,
            List<ChoiceOption> choices) {
    }
}
