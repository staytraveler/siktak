package com.siktak.slot.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.slot.application.SlotService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 운영자 회차 관리 (PRD 11장 회차 관리). */
@RestController
@RequestMapping("/api/v1/admin/slots")
@RequiredArgsConstructor
public class AdminSlotController {

    private final SlotService slotService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SlotResponse create(@Valid @RequestBody CreateSlotRequest request) {
        return SlotResponse.from(slotService.create(request));
    }

    @GetMapping
    public List<SlotResponse> list() {
        return slotService.findAll().stream().map(SlotResponse::from).toList();
    }
}
