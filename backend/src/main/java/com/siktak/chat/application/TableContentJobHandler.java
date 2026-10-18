package com.siktak.chat.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.siktak.common.schedule.JobTrigger;
import com.siktak.common.schedule.ScheduledJob;
import com.siktak.common.schedule.ScheduledJobHandler;

import lombok.RequiredArgsConstructor;

/** 행사 시작 시각 대화 콘텐츠 자동 게시 (CON-03). 대상 ID는 dining_tables.id. */
@Component
@RequiredArgsConstructor
public class TableContentJobHandler implements ScheduledJobHandler {

    private final TableGuidanceService guidanceService;

    @Override
    public String jobType() {
        return TableGuidanceService.CONTENT_JOB;
    }

    @Override
    public List<ScheduledJob> loadPending() {
        return guidanceService.pendingContents();
    }

    @Override
    public void execute(long tableId, JobTrigger trigger) {
        guidanceService.postContent(tableId);
    }
}
