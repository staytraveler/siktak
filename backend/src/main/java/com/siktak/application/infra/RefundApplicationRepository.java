package com.siktak.application.infra;

import java.util.List;

import org.springframework.data.repository.Repository;

import com.siktak.application.domain.Application;
import com.siktak.application.domain.status.PaymentStatus;

/** 환불 재시도 작업 적재용 조회 (6.1). */
public interface RefundApplicationRepository extends Repository<Application, Long> {

    List<Application> findByPaymentStatusOrderByIdAsc(PaymentStatus paymentStatus);
}
