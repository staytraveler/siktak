package com.siktak.slot.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.common.schedule.ScheduledJob;
import com.siktak.common.schedule.ScheduledJobRegistry;
import com.siktak.slot.api.CreateSlotRequest;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.domain.Region;
import com.siktak.slot.domain.SlotStatus;
import com.siktak.slot.infra.EventSlotRepository;
import com.siktak.slot.infra.RegionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SlotService {

    private final EventSlotRepository eventSlotRepository;
    private final RegionRepository regionRepository;
    private final ScheduledJobRegistry jobRegistry;
    private final Clock clock;

    @Transactional
    public EventSlot create(CreateSlotRequest request) {
        Region region = regionRepository.findById(request.regionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "지역을 찾을 수 없습니다."));
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw invalid("endsAt", "종료 시각은 시작 시각보다 늦어야 합니다.");
        }
        if (request.matchRunAt().isAfter(request.startsAt())) {
            throw invalid("matchRunAt", "매칭 실행 시각은 행사 시작 전이어야 합니다.");
        }
        if (request.applyDeadlineAt().isAfter(request.matchRunAt())) {
            throw invalid("applyDeadlineAt", "신청 마감은 매칭 실행 시각 이전이어야 합니다.");
        }
        EventSlot slot = eventSlotRepository.save(EventSlot.create(region, request.startsAt(), request.endsAt(),
                request.capacity(), request.applyDeadlineAt(), request.matchRunAt()));
        // 매칭 마감 시각에 자동 매칭 (커밋 후 메모리 일정에 등록, DB 폴링 없음)
        jobRegistry.schedule(new ScheduledJob(MatchRunJobHandler.JOB_TYPE, slot.getId(), slot.getMatchRunAt()));
        return slot;
    }

    public List<EventSlot> findAll() {
        return eventSlotRepository.findAllByOrderByStartsAtAscIdAsc();
    }

    /** 참가자에게 보여줄 모집 중 회차 (APP-01). */
    public List<EventSlot> findOpen() {
        Instant now = Instant.now(clock);
        return eventSlotRepository.findBySlotStatusOrderByStartsAtAscIdAsc(SlotStatus.OPEN).stream()
                .filter(slot -> slot.isOpenForApplication(now))
                .toList();
    }

    public EventSlot get(Long id) {
        return eventSlotRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "회차를 찾을 수 없습니다."));
    }

    public List<Region> findRegions() {
        return regionRepository.findByActiveTrueOrderByIdAsc();
    }

    private static BusinessException invalid(String field, String reason) {
        return new BusinessException(ErrorCode.INVALID_REQUEST, reason,
                Map.of("fields", List.of(Map.of("field", field, "reason", reason))));
    }
}
