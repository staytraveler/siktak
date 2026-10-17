package com.siktak.application.domain.status;

/** 결제 상태 payment_status — 신청 관점의 도메인 상태 (PRD 12장, states.md 3장). PG 거래 상태는 payments.tx_status. */
public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    REFUND_AVAILABLE,
    REFUND_REQUESTED,
    REFUND_PROCESSING,
    REFUNDED,
    REFUND_FAILED
}
