package com.siktak.slot.domain;

/** 회차 상태 (erd.md event_slots.slot_status). */
public enum SlotStatus {
    OPEN,
    CLOSED,
    MATCHED,
    DONE,
    CANCELED
}
