package com.siktak.application.domain.status;

import static com.siktak.application.domain.status.ActorType.OPERATOR;
import static com.siktak.application.domain.status.ActorType.PARTICIPANT;
import static com.siktak.application.domain.status.ActorType.PAYMENT_GATEWAY;
import static com.siktak.application.domain.status.ActorType.SCHEDULER;
import static com.siktak.application.domain.status.ActorType.SYSTEM;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.Getter;

/**
 * 허용 전이표 (docs/domain/states.md 2~5장). 표에 없는 전이는 할 수 없다.
 * {@code from}의 {@code null}은 "(없음)" — 해당 축의 상태가 아직 없는 경우다.
 */
@Getter
public enum Transition {

    // ---- 신청 application_status (states.md 2장) ----
    A1(StatusAxis.APPLICATION, from((Enum<?>) null), ApplicationStatus.DRAFT, PARTICIPANT),
    A2(StatusAxis.APPLICATION, from(ApplicationStatus.DRAFT), ApplicationStatus.SUBMITTED, PARTICIPANT),
    A3(StatusAxis.APPLICATION, from(ApplicationStatus.DRAFT), ApplicationStatus.CANCELED, PARTICIPANT),
    A4(StatusAxis.APPLICATION, from(ApplicationStatus.SUBMITTED), ApplicationStatus.CANCELED, PARTICIPANT),
    A5(StatusAxis.APPLICATION, from(ApplicationStatus.SUBMITTED), ApplicationStatus.CANCELED, SYSTEM),

    // ---- 결제 payment_status (states.md 3장) ----
    P1(StatusAxis.PAYMENT, from((Enum<?>) null), PaymentStatus.PENDING, SYSTEM),
    P1a(StatusAxis.PAYMENT, from((Enum<?>) null), PaymentStatus.PAID, SYSTEM),
    P2(StatusAxis.PAYMENT, from(PaymentStatus.PENDING), PaymentStatus.PAID, PAYMENT_GATEWAY),
    P3(StatusAxis.PAYMENT, from(PaymentStatus.PENDING), PaymentStatus.FAILED, PAYMENT_GATEWAY),
    P4(StatusAxis.PAYMENT, from(PaymentStatus.FAILED), PaymentStatus.PENDING, PARTICIPANT),
    P5(StatusAxis.PAYMENT, from(PaymentStatus.PAID), PaymentStatus.REFUND_AVAILABLE, SYSTEM),
    P6(StatusAxis.PAYMENT, from(PaymentStatus.REFUND_AVAILABLE), PaymentStatus.REFUND_REQUESTED, PARTICIPANT),
    P7(StatusAxis.PAYMENT, from(PaymentStatus.REFUND_AVAILABLE), PaymentStatus.PAID, PARTICIPANT),
    P7a(StatusAxis.PAYMENT, from(PaymentStatus.PAID), PaymentStatus.REFUND_REQUESTED, PARTICIPANT),
    P8(StatusAxis.PAYMENT, from(PaymentStatus.REFUND_REQUESTED), PaymentStatus.REFUND_PROCESSING, SYSTEM),
    P9(StatusAxis.PAYMENT, from(PaymentStatus.REFUND_PROCESSING), PaymentStatus.REFUNDED, PAYMENT_GATEWAY),
    P10(StatusAxis.PAYMENT, from(PaymentStatus.REFUND_PROCESSING), PaymentStatus.REFUND_FAILED, SYSTEM),
    P11(StatusAxis.PAYMENT, from(PaymentStatus.REFUND_FAILED), PaymentStatus.REFUND_PROCESSING, OPERATOR),
    P12(StatusAxis.PAYMENT, from(PaymentStatus.REFUND_FAILED), PaymentStatus.REFUNDED, OPERATOR),

    // ---- 매칭 match_status (states.md 4장) ----
    M1(StatusAxis.MATCH, from((Enum<?>) null), MatchStatus.WAITING, SYSTEM),
    M2(StatusAxis.MATCH, from(MatchStatus.WAITING), MatchStatus.MATCHING, SCHEDULER),
    M3(StatusAxis.MATCH, from(MatchStatus.MATCHING), MatchStatus.CONFIRMED, SYSTEM),
    M4(StatusAxis.MATCH, from(MatchStatus.MATCHING), MatchStatus.REALLOCATING, SYSTEM),
    M4a(StatusAxis.MATCH, from(MatchStatus.MATCHING, MatchStatus.REALLOCATING), MatchStatus.WAITING, SYSTEM),
    M5(StatusAxis.MATCH, from(MatchStatus.REALLOCATING), MatchStatus.CONFIRMED, SYSTEM),
    M6(StatusAxis.MATCH, from(MatchStatus.REALLOCATING), MatchStatus.ALTERNATIVE_OFFERED, SYSTEM),
    M7(StatusAxis.MATCH, from(MatchStatus.ALTERNATIVE_OFFERED), MatchStatus.TRANSFERRED, PARTICIPANT),
    M8(StatusAxis.MATCH, from(MatchStatus.ALTERNATIVE_OFFERED), MatchStatus.NO_MATCH, PARTICIPANT),
    M8a(StatusAxis.MATCH, from(MatchStatus.ALTERNATIVE_OFFERED), MatchStatus.NO_MATCH, SCHEDULER),
    M9(StatusAxis.MATCH, from(MatchStatus.ALTERNATIVE_OFFERED), MatchStatus.NO_MATCH, SYSTEM),
    M10(StatusAxis.MATCH, from(MatchStatus.CONFIRMED), MatchStatus.REALLOCATING, SYSTEM),
    M11(StatusAxis.MATCH, from(MatchStatus.MATCHING, MatchStatus.REALLOCATING), MatchStatus.EXCEPTION, SYSTEM),
    M12(StatusAxis.MATCH, from(MatchStatus.EXCEPTION),
            List.of(MatchStatus.WAITING, MatchStatus.CONFIRMED, MatchStatus.ALTERNATIVE_OFFERED), OPERATOR),

    // ---- 참석 attendance_status (states.md 5장) ----
    T1(StatusAxis.ATTENDANCE, from((Enum<?>) null), AttendanceStatus.SCHEDULED, SYSTEM),
    T2(StatusAxis.ATTENDANCE, from(AttendanceStatus.SCHEDULED), AttendanceStatus.ATTENDED, PARTICIPANT),
    T3(StatusAxis.ATTENDANCE, from(AttendanceStatus.SCHEDULED), AttendanceStatus.CANCELED_EARLY, PARTICIPANT),
    T4(StatusAxis.ATTENDANCE, from(AttendanceStatus.SCHEDULED), AttendanceStatus.CANCELED_LATE, PARTICIPANT),
    T5(StatusAxis.ATTENDANCE, from(AttendanceStatus.SCHEDULED), AttendanceStatus.NO_SHOW, OPERATOR);

    private final StatusAxis axis;
    /** 허용되는 이전 상태 (null 포함 가능 = 상태 없음) */
    private final Set<Enum<?>> fromStatuses;
    /** 허용되는 다음 상태. 대부분 1개, M12만 여러 개 */
    private final List<Enum<?>> toStatuses;
    private final ActorType actor;

    Transition(StatusAxis axis, Set<Enum<?>> from, Enum<?> to, ActorType actor) {
        this(axis, from, List.of(to), actor);
    }

    Transition(StatusAxis axis, Set<Enum<?>> from, List<? extends Enum<?>> to, ActorType actor) {
        this.axis = axis;
        this.fromStatuses = Collections.unmodifiableSet(from);
        this.toStatuses = List.copyOf(to);
        this.actor = actor;
    }

    public boolean allowsFrom(Enum<?> current) {
        return fromStatuses.contains(current);
    }

    public boolean allowsTo(Enum<?> target) {
        return toStatuses.contains(target);
    }

    /** 다음 상태가 하나뿐인 전이의 목표 상태. M12처럼 여러 개면 호출 측이 목표를 지정해야 한다. */
    public Enum<?> singleTarget() {
        if (toStatuses.size() != 1) {
            throw new IllegalStateException(name() + " has multiple targets; specify one");
        }
        return toStatuses.get(0);
    }

    private static Set<Enum<?>> from(Enum<?>... statuses) {
        // HashSet: "(없음)"을 null 원소로 담기 위해 Set.of 대신 사용
        return new HashSet<>(Arrays.asList(statuses));
    }

    /** 축별 전이 목록 (테스트·문서화용). */
    public static Set<Transition> of(StatusAxis axis) {
        EnumSet<Transition> result = EnumSet.noneOf(Transition.class);
        for (Transition t : values()) {
            if (t.axis == axis) {
                result.add(t);
            }
        }
        return result;
    }
}
