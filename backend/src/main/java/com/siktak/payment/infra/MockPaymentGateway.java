package com.siktak.payment.infra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.siktak.payment.domain.PaymentGateway;
import com.siktak.payment.domain.PaymentProvider;

/**
 * 모의 결제 (PRD PAY-01 해커톤 허용). 항상 승인하고, 같은 orderId에는 같은 결제 키를 돌려준다.
 * 거절·환불 실패 재현은 2.4 이후 데모 도구에서 추가한다.
 */
@Component
@ConditionalOnProperty(name = "siktak.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MOCK;
    }

    @Override
    public ApprovalResult approve(String orderId, long amount) {
        return ApprovalResult.success("mock_" + orderId);
    }
}
