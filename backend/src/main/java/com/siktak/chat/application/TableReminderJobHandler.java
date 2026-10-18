package com.siktak.chat.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.siktak.common.schedule.JobTrigger;
import com.siktak.common.schedule.ScheduledJob;
import com.siktak.common.schedule.ScheduledJobHandler;

import lombok.RequiredArgsConstructor;

/** 행사 1일 전 리마인드 (INF-07). 대상 ID는 dining_tables.id. */
@Component
@RequiredArgsConstructor
public class TableReminderJobHandler implements ScheduledJobHandler {

    private final TableGuidanceService guidanceService;

    @Override
    public String jobType() {
        return TableGuidanceService.REMINDER_JOB;
    }

    @Override
    public List<ScheduledJob> loadPending() {
        return guidanceService.pendingReminders();
    }

    @Override
    public void execute(long tableId, JobTrigger trigger) {
        guidanceService.postReminder(tableId);
    }
}
