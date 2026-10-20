package com.siktak.slot.domain;

import java.time.Instant;

/** 운영자가 새 회차를 열었다. 다음 모집 시작 알림(PRD 14장)을 보내는 쪽이 듣는다. */
public record SlotCreatedEvent(Long slotId, Long regionId, Instant startsAt) {
}
