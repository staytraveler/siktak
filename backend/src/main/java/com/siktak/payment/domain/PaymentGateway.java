package com.siktak.payment.domain;

/**
 * 결제 연동 포트 (CLAUDE.md 8장). 구현은 siktak.payment.provider 설정으로 고른다 (mock | toss).
 * 같은 orderId로 다시 호출해도 결과가 같아야 한다 (멱등).
 */
public interface PaymentGateway {

    PaymentProvider provider();

    ApprovalResult approve(String orderId, long amount);

    record ApprovalResult(boolean approved, String providerPaymentKey, String error) {

        public static ApprovalResult success(String providerPaymentKey) {
            return new ApprovalResult(true, providerPaymentKey, null);
        }

        public static ApprovalResult failure(String error) {
            return new ApprovalResult(false, null, error);
        }
    }
}
