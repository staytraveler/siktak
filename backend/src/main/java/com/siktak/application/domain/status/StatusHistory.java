package com.siktak.application.domain.status;

import com.siktak.common.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상태 변경 감사 로그 (erd.md status_histories). 시각은 created_at. */
@Getter
@Entity
@Table(name = "status_histories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StatusHistory extends BaseTimeEntity {

    public static final String TARGET_APPLICATION = "APPLICATION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusAxis axis;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status", nullable = false)
    private String toStatus;

    @Column(name = "transition_code", nullable = false)
    private String transitionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false)
    private ActorType actorType;

    @Column(name = "actor_id")
    private Long actorId;

    private String reason;

    @Column(name = "match_run_id")
    private Long matchRunId;

    public static StatusHistory record(String targetType, Long targetId, Transition transition,
            Enum<?> from, Enum<?> to, TransitionContext context, String reason) {
        StatusHistory history = new StatusHistory();
        history.targetType = targetType;
        history.targetId = targetId;
        history.axis = transition.getAxis();
        history.fromStatus = from == null ? null : from.name();
        history.toStatus = to.name();
        history.transitionCode = transition.name();
        history.actorType = context.actorType();
        history.actorId = context.actorId();
        history.reason = reason;
        history.matchRunId = context.matchRunId();
        return history;
    }
}
