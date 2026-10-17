package com.siktak.payment.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 결제 목적과 금액(원). 가격은 states.md 1장 D1. */
@Getter
@RequiredArgsConstructor
public enum PaymentPurpose {
    SINGLE_TICKET(9_900L),
    MEMBERSHIP_1M(35_000L),
    MEMBERSHIP_3M(75_000L),
    MEMBERSHIP_6M(120_000L);

    private final long amount;
}
