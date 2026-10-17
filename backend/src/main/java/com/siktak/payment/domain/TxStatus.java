package com.siktak.payment.domain;

/** PG 거래 자체의 상태 (erd.md payments.tx_status). 신청 관점의 상태는 applications.payment_status. */
public enum TxStatus {
    READY,
    APPROVED,
    FAILED,
    CANCELED,
    CANCEL_FAILED
}
