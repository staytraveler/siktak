package com.siktak.payment.infra;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.payment.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(String orderId);
}
