package com.siktak.application.application;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.application.domain.status.PaymentStatus;
import com.siktak.application.infra.RefundApplicationRepository;
import com.siktak.common.schedule.JobTrigger;
import com.siktak.common.schedule.ScheduledJob;
import com.siktak.common.schedule.ScheduledJobHandler;

import lombok.RequiredArgsConstructor;

/** 자동 환불 재시도 (states.md P10 이전). 실패하면 RefundService가 1분 뒤로 다시 등록한다. */
@Component
@RequiredArgsConstructor
public class RefundRetryJobHandler implements ScheduledJobHandler {

    private final RefundApplicationRepository refundApplicationRepository;
    private final RefundService refundService;
    private final Clock clock;

    @Override
    public String jobType() {
        return RefundService.RETRY_JOB;
    }

    /** 기동 시 환불 처리 중으로 남은 신청은 바로 다시 시도한다 (catch-up). */
    @Override
    @Transactional(readOnly = true)
    public List<ScheduledJob> loadPending() {
        return refundApplicationRepository.findByPaymentStatusOrderByIdAsc(PaymentStatus.REFUND_PROCESSING).stream()
                .map(a -> new ScheduledJob(RefundService.RETRY_JOB, a.getId(), clock.instant()))
                .toList();
    }

    @Override
    public void execute(long applicationId, JobTrigger trigger) {
        refundService.process(applicationId);
    }
}
