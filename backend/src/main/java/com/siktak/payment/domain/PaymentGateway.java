package com.siktak.payment.domain;

/**
 * 결제 연동 포트 (CLAUDE.md 8장). 구현은 siktak.payment.provider 설정으로 고른다 (mock | toss).
 * 같은 orderId로 다시 호출해도 결과가 같아야 한다 (멱등).
 */
public interface PaymentGateway {

    PaymentProvider provider();

    ApprovalResult approve(String orderId, long amount);

    /**
     * 결제를 취소(환불)한다. 같은 {@code idempotencyKey}로 다시 호출하면 PG는 취소를 한 번만 한다 (states.md P8).
     */
    CancelResult cancel(String providerPaymentKey, long amount, String idempotencyKey);

    record ApprovalResult(boolean approved, String providerPaymentKey, String error) {

        public static ApprovalResult success(String providerPaymentKey) {
            return new ApprovalResult(true, providerPaymentKey, null);
        }

        public static ApprovalResult failure(String error) {
            return new ApprovalResult(false, null, error);
        }
    }

    record CancelResult(boolean canceled, String error) {

        public static CancelResult success() {
            return new CancelResult(true, null);
        }

        public static CancelResult failure(String error) {
            return new CancelResult(false, error);
        }
    }
}
