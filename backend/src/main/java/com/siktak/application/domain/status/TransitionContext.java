package com.siktak.application.domain.status;

/** 전이를 일으킨 주체와 사유. status_histories에 그대로 기록된다. */
public record TransitionContext(ActorType actorType, Long actorId, String reason, Long matchRunId) {

    public static TransitionContext of(ActorType actorType, Long actorId, String reason) {
        return new TransitionContext(actorType, actorId, reason, null);
    }

    public static TransitionContext system(String reason) {
        return new TransitionContext(ActorType.SYSTEM, null, reason, null);
    }

    public TransitionContext withActor(ActorType type) {
        return new TransitionContext(type, actorId, reason, matchRunId);
    }

    public TransitionContext withMatchRun(Long runId) {
        return new TransitionContext(actorType, actorId, reason, runId);
    }
}
