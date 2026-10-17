package com.siktak.payment.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.payment.domain.Payment;
import com.siktak.payment.domain.PaymentGateway;
import com.siktak.payment.domain.PaymentPurpose;
import com.siktak.payment.domain.TxStatus;
import com.siktak.payment.infra.PaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * PG 거래(payments) 관리. 신청의 결제 상태(applications.payment_status) 전이는 호출 측(application 도메인)이 한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final Clock clock;

    /** 결제 대기 거래를 만든다. 같은 orderId가 있으면 그대로 돌려준다 (멱등). */
    @Transactional
    public Payment prepare(Long userId, PaymentPurpose purpose, String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseGet(() -> paymentRepository.save(
                        Payment.ready(userId, purpose, paymentGateway.provider(), orderId)));
    }

    /** 결제를 승인한다. 이미 승인된 거래면 PG를 다시 호출하지 않는다 (멱등). 승인되면 true. */
    @Transactional
    public boolean approve(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
        if (payment.getTxStatus() == TxStatus.APPROVED) {
            return true;
        }
        PaymentGateway.ApprovalResult result = paymentGateway.approve(payment.getOrderId(), payment.getAmount());
        if (result.approved()) {
            payment.approve(result.providerPaymentKey(), Instant.now(clock));
            return true;
        }
        payment.fail(result.error());
        return false;
    }
}
