package com.siktak.slot.application;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.common.schedule.JobTrigger;
import com.siktak.common.schedule.ScheduledJob;
import com.siktak.common.schedule.ScheduledJobHandler;
import com.siktak.matching.application.MatchingService;
import com.siktak.slot.domain.SlotStatus;
import com.siktak.slot.infra.EventSlotRepository;

import lombok.RequiredArgsConstructor;

/**
 * 회차 매칭 마감 시각(match_run_at)에 자동 매칭을 실행한다 (MAT-01).
 * 실행 자체는 MatchingService가 하며 run_key로 멱등이다 — catch-up·재시도·중복 실행에 안전.
 */
@Component
@RequiredArgsConstructor
public class MatchRunJobHandler implements ScheduledJobHandler {

    public static final String JOB_TYPE = "MATCH_RUN";

    private final EventSlotRepository eventSlotRepository;
    private final MatchingService matchingService;

    @Override
    public String jobType() {
        return JOB_TYPE;
    }

    /** 아직 매칭되지 않은 모집 중 회차. 기동 시 1회만 호출된다. */
    @Override
    @Transactional(readOnly = true)
    public List<ScheduledJob> loadPending() {
        return eventSlotRepository.findBySlotStatusOrderByStartsAtAscIdAsc(SlotStatus.OPEN).stream()
                .map(slot -> new ScheduledJob(JOB_TYPE, slot.getId(), slot.getMatchRunAt()))
                .toList();
    }

    @Override
    public void execute(long slotId, JobTrigger trigger) {
        matchingService.runForSlot(slotId, trigger.triggeredBy());
    }
}
