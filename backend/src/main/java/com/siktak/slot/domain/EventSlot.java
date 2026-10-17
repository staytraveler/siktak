package com.siktak.slot.domain;

import java.time.Instant;

import com.siktak.common.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회차 = 날짜·시간·지역 조합 (erd.md 3-2). */
@Getter
@Entity
@Table(name = "event_slots")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventSlot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id")
    private Region region;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "apply_deadline_at", nullable = false)
    private Instant applyDeadlineAt;

    @Column(name = "match_run_at", nullable = false)
    private Instant matchRunAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "slot_status", nullable = false)
    private SlotStatus slotStatus = SlotStatus.OPEN;

    public static EventSlot create(Region region, Instant startsAt, Instant endsAt, int capacity,
            Instant applyDeadlineAt, Instant matchRunAt) {
        EventSlot slot = new EventSlot();
        slot.region = region;
        slot.startsAt = startsAt;
        slot.endsAt = endsAt;
        slot.capacity = capacity;
        slot.applyDeadlineAt = applyDeadlineAt;
        slot.matchRunAt = matchRunAt;
        return slot;
    }

    public boolean isOpenForApplication(Instant now) {
        return slotStatus == SlotStatus.OPEN && now.isBefore(applyDeadlineAt);
    }

    public void markMatched() {
        this.slotStatus = SlotStatus.MATCHED;
    }
}
