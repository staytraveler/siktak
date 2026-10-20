package com.siktak.application.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.siktak.application.domain.Application;
import com.siktak.application.domain.PassType;
import com.siktak.application.domain.status.ActorType;
import com.siktak.application.domain.status.ApplicationStatus;
import com.siktak.application.domain.status.AttendanceStatus;
import com.siktak.application.domain.status.MatchStatus;
import com.siktak.application.domain.status.PaymentStatus;
import com.siktak.application.domain.status.Transition;
import com.siktak.application.domain.status.TransitionContext;
import com.siktak.chat.application.ChatService;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.exception.application.ExceptionCaseService;
import com.siktak.exception.domain.CaseType;
import com.siktak.feedback.application.AttendanceService;
import com.siktak.matching.application.EngineMatchingService;
import com.siktak.matching.domain.TableMember;
import com.siktak.matching.infra.TableMemberRepository;
import com.siktak.notification.application.GuidanceFormat;
import com.siktak.notification.application.NotificationService;
import com.siktak.payment.domain.UserPass;
import com.siktak.payment.infra.UserPassRepository;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.infra.EventSlotRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 신청·참석 취소 (states.md 8장 취소·환불 정리, PRD 2·14·15장).
 * <ul>
 *   <li>확정 전 신청 취소: 결제 전이면 바로 취소(A4). 결제 후면 행사 2일 전까지만 — 1회권은 자동 환불(P7a), 크레딧은 복원,
 *       멤버십은 처리 없음. 매칭 대기 중이면 매칭도 종료한다(M13).</li>
 *   <li>확정 후 참석 취소: 2일 전까지 사전 취소(T3, 환불·복원·단체방 퇴장), 그 뒤는 당일·제한 시점 이후 취소(T4, 환불 없음,
 *       이용 제한 검토 대상 예외, 단체방은 남는다). 어느 쪽이든 테이블 인원을 다시 확인해 4명 미만이면 재배치한다.</li>
 * </ul>
 * 환불은 취소 트랜잭션이 커밋된 뒤 PG(모의)를 부른다 — RefundService가 멱등·재시도·예외 전환을 맡는다.
 */
@Slf4j
@Service
public class CancellationService {

    public enum Kind {
        APPLICATION,
        ATTENDANCE
    }

    /** 화면용 취소 안내. */
    public record CancelInfo(boolean canCancel, Kind kind, boolean refund, String message) {
    }

    private record Plan(CancelInfo info, EventSlot slot, boolean early) {
    }

    private final ApplicationService applicationService;
    private final StatusTransitionService transitionService;
    private final UserPassRepository userPassRepository;
    private final RefundService refundService;
    private final AttendanceService attendanceService;
    private final TableMemberRepository tableMemberRepository;
    private final EventSlotRepository eventSlotRepository;
    private final EngineMatchingService matchingService;
    private final ChatService chatService;
    private final ExceptionCaseService exceptionCaseService;
    private final NotificationService notificationService;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration refundCutoff;

    public CancellationService(ApplicationService applicationService, StatusTransitionService transitionService,
            UserPassRepository userPassRepository, RefundService refundService, AttendanceService attendanceService,
            TableMemberRepository tableMemberRepository, EventSlotRepository eventSlotRepository,
            EngineMatchingService matchingService, ChatService chatService, ExceptionCaseService exceptionCaseService,
            NotificationService notificationService, PlatformTransactionManager transactionManager, Clock clock,
            @Value("${siktak.policy.refund-cutoff-days:2}") long refundCutoffDays) {
        this.applicationService = applicationService;
        this.transitionService = transitionService;
        this.userPassRepository = userPassRepository;
        this.refundService = refundService;
        this.attendanceService = attendanceService;
        this.tableMemberRepository = tableMemberRepository;
        this.eventSlotRepository = eventSlotRepository;
        this.matchingService = matchingService;
        this.chatService = chatService;
        this.exceptionCaseService = exceptionCaseService;
        this.notificationService = notificationService;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.refundCutoff = Duration.ofDays(refundCutoffDays);
    }

    // ------------------------------------------------------------------ 안내

    @Transactional(readOnly = true)
    public CancelInfo info(Long userId, Long applicationId) {
        Application application = applicationService.getOwned(userId, applicationId);
        return plan(application, Instant.now(clock)).info();
    }

    private Plan plan(Application a, Instant now) {
        if (a.getApplicationStatus() != ApplicationStatus.SUBMITTED) {
            return no("이미 취소됐거나 끝난 신청이에요.");
        }
        MatchStatus match = a.getMatchStatus();
        PaymentStatus payment = a.getPaymentStatus();
        if (match == null || match == MatchStatus.WAITING) {
            if (payment != PaymentStatus.PAID) {
                return new Plan(new CancelInfo(true, Kind.APPLICATION, false, "아직 결제 전이라 바로 취소돼요."), null, true);
            }
            EventSlot first = earliestChoice(a);
            if (first != null && !now.isBefore(first.getStartsAt().minus(refundCutoff))) {
                return no("진행 " + refundCutoff.toDays() + "일 전 이후에는 신청을 취소하거나 환불할 수 없어요.");
            }
            return new Plan(new CancelInfo(true, Kind.APPLICATION, a.getPassType() == PassType.SINGLE,
                    earlyMessage(a.getPassType())), first, true);
        }
        if (match == MatchStatus.CONFIRMED) {
            EventSlot slot = a.getAssignedSlotId() == null ? null : eventSlotRepository.findById(a.getAssignedSlotId())
                    .orElse(null);
            if (slot == null || attendanceService.statusOf(a.getId()) != AttendanceStatus.SCHEDULED) {
                return no("이미 참석 처리됐거나 취소된 신청이에요.");
            }
            if (!now.isBefore(slot.getStartsAt())) {
                return no("행사가 이미 시작됐어요. 체크인해 주세요.");
            }
            boolean early = now.isBefore(slot.getStartsAt().minus(refundCutoff));
            if (early) {
                return new Plan(new CancelInfo(true, Kind.ATTENDANCE, a.getPassType() == PassType.SINGLE,
                        earlyMessage(a.getPassType()) + " 단체방에서도 나가게 돼요."), slot, true);
            }
            return new Plan(new CancelInfo(true, Kind.ATTENDANCE, false,
                    "진행 " + refundCutoff.toDays() + "일 전 이후 취소라 환불·이용권 복원이 없고, 이용 제한 검토 대상이 될 수 있어요. "
                            + "단체방에는 남아요."), slot, false);
        }
        if (match == MatchStatus.ALTERNATIVE_OFFERED) {
            return no("대체 일정·이용권 보관·환불 중에서 먼저 선택해 주세요.");
        }
        return no("지금은 취소할 수 없는 상태예요.");
    }

    private static Plan no(String message) {
        return new Plan(new CancelInfo(false, null, false, message), null, false);
    }

    private static String earlyMessage(PassType type) {
        return switch (type) {
            case SINGLE -> "취소하면 결제 금액을 자동으로 환불해요.";
            case CREDIT -> "취소하면 사용한 보관 이용권이 돌아와요.";
            case MEMBERSHIP -> "취소해도 멤버십은 그대로 유지돼요.";
        };
    }

    private EventSlot earliestChoice(Application a) {
        return a.getSlotChoicesByPriority().stream()
                .map(c -> eventSlotRepository.findById(c.getEventSlotId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .min(Comparator.comparing(EventSlot::getStartsAt)).orElse(null);
    }

    // ------------------------------------------------------------------ 취소

    /** 취소를 처리한다. 같은 요청을 다시 보내도 이미 취소된 신청이면 거절만 한다. */
    public void cancel(Long userId, Long applicationId) {
        Boolean needRefund;
        try {
            needRefund = tx.execute(status -> cancelInTx(userId, applicationId));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "다른 요청이 먼저 처리되었습니다. 상태를 다시 확인해 주세요.");
        }
        if (Boolean.TRUE.equals(needRefund)) {
            // P7a 직후 바로 P8 (states.md). 취소 트랜잭션이 커밋된 뒤에 PG를 부른다
            refundService.process(applicationId);
        }
    }

    private boolean cancelInTx(Long userId, Long applicationId) {
        Application a = applicationService.getOwned(userId, applicationId);
        Instant now = Instant.now(clock);
        Plan plan = plan(a, now);
        if (!plan.info().canCancel()) {
            throw new BusinessException(ErrorCode.CONFLICT, plan.info().message());
        }
        return plan.info().kind() == Kind.APPLICATION ? cancelApplication(a, plan) : cancelAttendance(a, plan, now);
    }

    /** 확정 전 신청 취소: M13(대기 중이면) → 환불·복원 → A4. */
    private boolean cancelApplication(Application a, Plan plan) {
        TransitionContext participant = TransitionContext.of(ActorType.PARTICIPANT, a.getUserId(), "신청 취소");
        boolean refund = false;
        if (a.getMatchStatus() == MatchStatus.WAITING) {
            transitionService.apply(a, Transition.M13, participant);
        }
        if (a.getPaymentStatus() == PaymentStatus.PAID) {
            refund = settle(a, participant);
        } else {
            // 결제 대기·실패 상태의 취소: 결제 오류 예외가 열려 있으면 닫는다
            exceptionCaseService.resolve("PAYMENT_ERROR:application-" + a.getId(), null, "신청 취소로 해결됨");
        }
        transitionService.apply(a, Transition.A4, participant);
        return refund;
    }

    /** 이용권별 후처리. 1회권이면 환불 요청(P7a)을 걸고 true, 크레딧이면 복원, 멤버십이면 없음. */
    private boolean settle(Application a, TransitionContext participant) {
        switch (a.getPassType()) {
            case SINGLE -> {
                transitionService.apply(a, Transition.P7a, participant);
                return true;
            }
            case CREDIT -> userPassRepository.findFirstByUsedApplicationId(a.getId()).ifPresent(UserPass::restore);
            case MEMBERSHIP -> {
                // 멤버십은 기간권이라 취소해도 처리할 것이 없다
            }
        }
        return false;
    }

    /** 확정 후 참석 취소: T3/T4 → (사전이면) 환불·복원·단체방 퇴장 → 테이블 인원 재확인. */
    private boolean cancelAttendance(Application a, Plan plan, Instant now) {
        boolean early = plan.early();
        attendanceService.cancel(a.getUserId(), a.getId(), early);
        TransitionContext participant = TransitionContext.of(ActorType.PARTICIPANT, a.getUserId(),
                early ? "참석 사전 취소" : "참석 취소 (2일 전 이후)");
        TableMember member = tableMemberRepository.findByApplicationIdAndActiveTrue(a.getId()).orElse(null);
        boolean refund = false;
        if (early) {
            refund = settle(a, participant);
        } else {
            // T4: 환불·복원 없음, 이용 제한 검토 대상 (states.md T4). 운영자 예외 처리함의 "당일 취소·노쇼 후보"에 올린다
            exceptionCaseService.open(CaseType.NO_SHOW_CANDIDATE, "LATE_CANCEL:application-" + a.getId(), a.getId(),
                    member == null ? null : member.getDiningTableId(),
                    "행사 " + refundCutoff.toDays() + "일 전 이후에 참석을 취소했습니다. 이용 제한 검토 대상입니다.",
                    Map.of("userId", a.getUserId(), "eventSlotId", plan.slot().getId()));
        }
        if (member != null) {
            member.leave(now);
            if (early) {
                // 사전 취소는 단체방에서도 나간다. 당일 취소는 단체방 퇴장 불가 정책에 따라 남는다 (PRD 2장)
                chatService.removeMember(member.getDiningTableId(), a.getUserId());
            }
        }
        notificationService.enqueue(a.getUserId(), "APPLICATION_CANCELED",
                Map.of("slotLabel", GuidanceFormat.slotLabel(plan.slot()), "detail", plan.info().message()),
                Map.of("applicationId", a.getId(), "link", "/app/applications/" + a.getId()),
                "APPLICATION_CANCELED:attendance-" + a.getId());
        if (member != null) {
            // 인원이 줄어든 테이블 재확인: 4명 이상이면 유지, 미만이면 다른 테이블로 재배치 (PRD 15장)
            tableMemberRepository.flush();
            matchingService.reallocateAfterCancel(member.getDiningTableId(), a.getId());
        }
        return refund;
    }
}
