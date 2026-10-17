package com.siktak.matching.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.siktak.common.schedule.JobTrigger;
import com.siktak.common.schedule.ScheduledJob;
import com.siktak.common.schedule.ScheduledJobHandler;

import lombok.RequiredArgsConstructor;

/** 대체 일정 응답 기한(respond_by) 경과 시 보관 처리 (states.md M8a, D6·D9). 대상 ID는 match_resolutions.id. */
@Component
@RequiredArgsConstructor
public class ResolutionTimeoutJobHandler implements ScheduledJobHandler {

    private final ResolutionService resolutionService;

    @Override
    public String jobType() {
        return ResolutionService.TIMEOUT_JOB;
    }

    @Override
    public List<ScheduledJob> loadPending() {
        return resolutionService.pendingTimeouts();
    }

    @Override
    public void execute(long resolutionId, JobTrigger trigger) {
        resolutionService.autoKeep(resolutionId, trigger);
    }
}
