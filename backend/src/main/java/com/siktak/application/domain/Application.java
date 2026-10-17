package com.siktak.application.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.siktak.application.domain.status.ApplicationStatus;
import com.siktak.application.domain.status.MatchStatus;
import com.siktak.application.domain.status.PaymentStatus;
import com.siktak.application.domain.status.StatusAxis;
import com.siktak.application.domain.status.Transition;
import com.siktak.common.domain.BaseTimeEntity;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 신청. 신청·결제·매칭 상태를 각각 별도 컬럼으로 갖는다 (PRD 12장).
 * 상태는 {@link #applyTransition}로만 바뀌며, 이 메서드는 StatusTransitionService만 호출한다.
 */
@Getter
@Entity
@Table(name = "applications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "pass_type", nullable = false)
    private PassType passType;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "user_pass_id")
    private Long userPassId;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_status", nullable = false)
    private ApplicationStatus applicationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status")
    private MatchStatus matchStatus;

    @Column(name = "assigned_slot_id")
    private Long assignedSlotId;

    @Column(name = "transferred_from_id")
    private Long transferredFromId;

    @Column(name = "payment_due_at")
    private Instant paymentDueAt;

    @Column(name = "match_fail_reason")
    private String matchFailReason;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ApplicationSlotChoice> slotChoices = new ArrayList<>();

    /**
     * 새 신청을 만든다. 상태가 없는 상태로 만들고, 저장 전에 StatusTransitionService로 A1(→DRAFT)을 적용한다.
     */
    public static Application newApplication(Long userId, PassType passType) {
        Application application = new Application();
        application.userId = userId;
        application.passType = passType;
        return application;
    }

    /**
     * 일정 이동(M7, states.md D3)으로 생기는 새 신청. 원래 신청의 결제·이용권을 그대로 참조하고 새 결제를 만들지 않는다.
     * 상태는 비어 있으며, 호출 측이 A1·A2·P1a·M1을 적용한다.
     */
    public static Application transferOf(Application original, Long slotId) {
        Application application = newApplication(original.userId, original.passType);
        application.paymentId = original.paymentId;
        application.userPassId = original.userPassId;
        application.transferredFromId = original.id;
        application.chooseSlots(List.of(slotId));
        return application;
    }

    /** 회차 후보를 선택 순서대로 추가한다 (priority 1이 최우선, matching.md MD7). */
    public void chooseSlots(List<Long> slotIdsInPriorityOrder) {
        slotChoices.clear();
        for (int i = 0; i < slotIdsInPriorityOrder.size(); i++) {
            slotChoices.add(new ApplicationSlotChoice(this, slotIdsInPriorityOrder.get(i), i + 1));
        }
    }

    public List<ApplicationSlotChoice> getSlotChoicesByPriority() {
        return slotChoices.stream().sorted(Comparator.comparingInt(ApplicationSlotChoice::getPriority)).toList();
    }

    public boolean hasChosenSlot(Long slotId) {
        return slotChoices.stream().anyMatch(c -> c.getEventSlotId().equals(slotId));
    }

    public Enum<?> currentStatus(StatusAxis axis) {
        return switch (axis) {
            case APPLICATION -> applicationStatus;
            case PAYMENT -> paymentStatus;
            case MATCH -> matchStatus;
            case ATTENDANCE -> throw new IllegalArgumentException("참석 상태는 attendances 테이블에서 관리한다");
        };
    }

    /**
     * 전이를 적용한다. 허용 전이표 밖이면 예외. 이미 목표 상태면 변경하지 않고 false를 반환한다 (no-op).
     * StatusTransitionService 외에서 호출하지 않는다 — 이력(audit log) 기록이 빠지기 때문이다.
     */
    public boolean applyTransition(Transition transition, Enum<?> target) {
        if (!transition.allowsTo(target)) {
            throw new IllegalArgumentException(transition + " cannot move to " + target);
        }
        Enum<?> current = currentStatus(transition.getAxis());
        if (current == target) {
            return false;
        }
        if (!transition.allowsFrom(current)) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "현재 상태에서 할 수 없는 처리입니다: " + transition.getAxis() + " " + current + " → " + target,
                    java.util.Map.of("transition", transition.name(),
                            "axis", transition.getAxis().name(),
                            "from", String.valueOf(current),
                            "to", target.name()));
        }
        switch (transition.getAxis()) {
            case APPLICATION -> applicationStatus = (ApplicationStatus) target;
            case PAYMENT -> paymentStatus = (PaymentStatus) target;
            case MATCH -> matchStatus = (MatchStatus) target;
            case ATTENDANCE -> throw new IllegalArgumentException("참석 상태는 attendances 테이블에서 관리한다");
        }
        return true;
    }

    public void markSubmitted(Instant at) {
        this.submittedAt = at;
    }

    public void linkPayment(Long paymentId, Instant dueAt) {
        this.paymentId = paymentId;
        this.paymentDueAt = dueAt;
    }

    public void assignSlot(Long slotId) {
        this.assignedSlotId = slotId;
    }

    /** 매칭 미배치·실패 시 참가자에게 보여줄 문구 (matching.md 7-2). 상태가 아니라 안내 문구라 이력은 남기지 않는다. */
    public void recordMatchFailReason(String reason) {
        this.matchFailReason = reason;
    }
}
