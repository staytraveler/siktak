package com.siktak.slot.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.application.infra.ApplicationRepository;
import com.siktak.slot.application.SlotService;
import com.siktak.slot.domain.EventSlot;

import lombok.RequiredArgsConstructor;

/** 참가자용 회차·지역 조회 (APP-01). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;
    private final ApplicationRepository applicationRepository;

    @GetMapping("/slots")
    public List<SlotResponse> openSlots() {
        List<EventSlot> slots = slotService.findOpen();
        Map<Long, Long> applied = new HashMap<>();
        if (!slots.isEmpty()) {
            for (Object[] row : applicationRepository.countActiveBySlot(slots.stream().map(EventSlot::getId).toList())) {
                applied.put((Long) row[0], (Long) row[1]);
            }
        }
        // 남은 자리 = 정원 - 이 회차를 고른 진행 중 신청 수 (최소 0).
        // 실제 배치는 매칭이 정하므로 0이어도 신청은 막지 않는다.
        return slots.stream()
                .map(s -> SlotResponse.from(s, (int) Math.max(0, s.getCapacity() - applied.getOrDefault(s.getId(), 0L))))
                .toList();
    }

    @GetMapping("/regions")
    public List<RegionResponse> regions() {
        return slotService.findRegions().stream().map(RegionResponse::from).toList();
    }
}
