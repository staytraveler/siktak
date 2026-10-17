package com.siktak.payment.domain;

import java.time.Instant;

import com.siktak.common.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** PG 거래 기록 (erd.md payments). order_id가 결제 멱등 키다. */
@Getter
@Entity
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentPurpose purpose;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Column(name = "provider_payment_key")
    private String providerPaymentKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "tx_status", nullable = false)
    private TxStatus txStatus;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "refund_key", unique = true)
    private String refundKey;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error")
    private String lastError;

    public static Payment ready(Long userId, PaymentPurpose purpose, PaymentProvider provider, String orderId) {
        Payment payment = new Payment();
        payment.userId = userId;
        payment.purpose = purpose;
        payment.amount = purpose.getAmount();
        payment.provider = provider;
        payment.orderId = orderId;
        payment.txStatus = TxStatus.READY;
        return payment;
    }

    public void approve(String providerPaymentKey, Instant at) {
        this.providerPaymentKey = providerPaymentKey;
        this.txStatus = TxStatus.APPROVED;
        this.approvedAt = at;
        this.lastError = null;
    }

    public void fail(String error) {
        this.txStatus = TxStatus.FAILED;
        this.lastError = error;
    }
}
