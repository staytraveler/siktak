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
        SlotStatus slotStatus,
        Integer remainingSeats) {

    public static SlotResponse from(EventSlot slot) {
        return from(slot, null);
    }

    /** remainingSeats는 참가자 회차 목록(APP-01)에서만 채운다. 운영자·신청 응답에서는 null. */
    public static SlotResponse from(EventSlot slot, Integer remainingSeats) {
        return new SlotResponse(slot.getId(), slot.getRegion().getId(), slot.getRegion().getName(),
                slot.getStartsAt(), slot.getEndsAt(), slot.getCapacity(),
                slot.getApplyDeadlineAt(), slot.getMatchRunAt(), slot.getSlotStatus(), remainingSeats);
    }
}
