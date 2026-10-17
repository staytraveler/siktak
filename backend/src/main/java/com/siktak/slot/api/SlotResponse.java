package com.siktak.slot.api;

import java.time.Instant;

import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.domain.SlotStatus;

public record SlotResponse(
        Long id,
        Long regionId,
        String regionName,
        Instant startsAt,
        Instant endsAt,
        int capacity,
        Instant applyDeadlineAt,
        Instant matchRunAt,
        SlotStatus slotStatus) {

    public static SlotResponse from(EventSlot slot) {
        return new SlotResponse(slot.getId(), slot.getRegion().getId(), slot.getRegion().getName(),
                slot.getStartsAt(), slot.getEndsAt(), slot.getCapacity(),
                slot.getApplyDeadlineAt(), slot.getMatchRunAt(), slot.getSlotStatus());
    }
}
