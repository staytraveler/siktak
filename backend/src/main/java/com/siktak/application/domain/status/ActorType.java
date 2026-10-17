package com.siktak.application.domain.status;

/** 상태 변경 주체 (states.md 0장). */
public enum ActorType {
    PARTICIPANT,
    SCHEDULER,
    SYSTEM,
    PAYMENT_GATEWAY,
    OPERATOR
}
