package com.siktak.payment.infra;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.siktak.payment.domain.PaymentGateway;
import com.siktak.payment.domain.PaymentProvider;

/**
 * 모의 결제 (PRD PAY-01 해커톤 허용). 항상 승인하고, 같은 orderId에는 같은 결제 키를 돌려준다.
 * <p>취소(환불)는 같은 멱등 키로 다시 와도 한 번만 취소한 것으로 센다.
 * {@code siktak.payment.mock.fail-cancel=true}면 취소가 항상 실패한다 (환불 실패·예외 경로 시연용).
 */
@Component
@ConditionalOnProperty(name = "siktak.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    private final Map<String, AtomicInteger> cancelCalls = new ConcurrentHashMap<>();
    private final Map<String, Boolean> canceled = new ConcurrentHashMap<>();
    private volatile boolean failCancel;
    private volatile boolean failApprove;

    public MockPaymentGateway(@Value("${siktak.payment.mock.fail-cancel:false}") boolean failCancel,
            @Value("${siktak.payment.mock.fail-approve:false}") boolean failApprove) {
        this.failCancel = failCancel;
        this.failApprove = failApprove;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MOCK;
    }

    @Override
    public ApprovalResult approve(String orderId, long amount) {
        if (failApprove) {
            return ApprovalResult.failure("모의 결제: 승인 실패 주입(siktak.payment.mock.fail-approve)");
        }
        return ApprovalResult.success("mock_" + orderId);
    }

    /** 승인 실패 주입을 켜고 끈다 (결제 오류 예외 경로 시연용, MAT-13). */
    public void setFailApprove(boolean failApprove) {
        this.failApprove = failApprove;
    }

    public boolean isFailApprove() {
        return failApprove;
    }

    @Override
    public CancelResult cancel(String providerPaymentKey, long amount, String idempotencyKey) {
        cancelCalls.computeIfAbsent(idempotencyKey, k -> new AtomicInteger()).incrementAndGet();
        if (canceled.containsKey(idempotencyKey)) {
            return CancelResult.success();
        }
        if (failCancel) {
            return CancelResult.failure("모의 결제: 취소 실패 주입(siktak.payment.mock.fail-cancel)");
        }
        canceled.put(idempotencyKey, Boolean.TRUE);
        return CancelResult.success();
    }

    /** 실패 주입을 켜고 끈다 (테스트·데모 도구용). */
    public void setFailCancel(boolean failCancel) {
        this.failCancel = failCancel;
    }

    /** 이 멱등 키로 PG 취소 API가 불린 횟수 (중복 환불 검증용). */
    public int cancelCallCount(String idempotencyKey) {
        AtomicInteger count = cancelCalls.get(idempotencyKey);
        return count == null ? 0 : count.get();
    }
}
