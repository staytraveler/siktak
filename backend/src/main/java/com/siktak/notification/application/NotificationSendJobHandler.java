package com.siktak.notification.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.siktak.common.schedule.JobTrigger;
import com.siktak.common.schedule.ScheduledJob;
import com.siktak.common.schedule.ScheduledJobHandler;

import lombok.RequiredArgsConstructor;

/** 예약·재시도 알림 발송 (INF-07·08). 대상 ID는 notifications.id. */
@Component
@RequiredArgsConstructor
public class NotificationSendJobHandler implements ScheduledJobHandler {

    private final NotificationService notificationService;

    @Override
    public String jobType() {
        return NotificationService.SEND_JOB;
    }

    @Override
    public List<ScheduledJob> loadPending() {
        return notificationService.pendingJobs();
    }

    @Override
    public void execute(long notificationId, JobTrigger trigger) {
        notificationService.send(notificationId);
    }
}
