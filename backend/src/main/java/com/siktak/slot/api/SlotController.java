package com.siktak.slot.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.slot.application.SlotService;

import lombok.RequiredArgsConstructor;

/** 참가자용 회차·지역 조회 (APP-01). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;

    @GetMapping("/slots")
    public List<SlotResponse> openSlots() {
        return slotService.findOpen().stream().map(SlotResponse::from).toList();
    }

    @GetMapping("/regions")
    public List<RegionResponse> regions() {
        return slotService.findRegions().stream().map(RegionResponse::from).toList();
    }
}
