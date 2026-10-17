package com.siktak.payment.domain;

/** 보유 이용권 종류 (erd.md user_passes.pass_type, states.md 1장). */
public enum UserPassType {
    MEMBERSHIP_1M,
    MEMBERSHIP_3M,
    MEMBERSHIP_6M,
    /** 매칭 실패 후 보관한 1회권 (states.md D2) */
    SINGLE_CREDIT
}
