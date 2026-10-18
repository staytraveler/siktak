package com.siktak.feedback.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.siktak.common.schedule.JobTrigger;
import com.siktak.common.schedule.ScheduledJob;
import com.siktak.common.schedule.ScheduledJobHandler;

import lombok.RequiredArgsConstructor;

/** 행사 종료 시각(ends_at)에 노쇼 후보 표시·만남 이력 생성·종료 이벤트 발행 (states.md 7장). 대상 ID는 회차 ID. */
@Component
@RequiredArgsConstructor
public class EventEndJobHandler implements ScheduledJobHandler {

    private final AttendanceService attendanceService;

    @Override
    public String jobType() {
        return AttendanceService.EVENT_END_JOB;
    }

    @Override
    public List<ScheduledJob> loadPending() {
        return attendanceService.pendingEventEnds();
    }

    @Override
    public void execute(long slotId, JobTrigger trigger) {
        attendanceService.processEventEnd(slotId);
    }
}
