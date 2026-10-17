package com.siktak.venue.domain;

import com.siktak.common.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회차별 식당 수용량 (erd.md venue_slot_capacities). 동시 배정은 낙관적 락으로 막는다. */
@Getter
@Entity
@Table(name = "venue_slot_capacities")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VenueSlotCapacity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "venue_id", nullable = false)
    private Long venueId;

    @Column(name = "event_slot_id", nullable = false)
    private Long eventSlotId;

    @Column(name = "max_tables", nullable = false)
    private int maxTables;

    @Column(name = "max_seats_per_table", nullable = false)
    private int maxSeatsPerTable;

    @Column(name = "reserved_tables", nullable = false)
    private int reservedTables;

    @Version
    private Long version;

    public static VenueSlotCapacity of(Long venueId, Long eventSlotId, int maxTables, int maxSeatsPerTable) {
        VenueSlotCapacity c = new VenueSlotCapacity();
        c.venueId = venueId;
        c.eventSlotId = eventSlotId;
        c.maxTables = maxTables;
        c.maxSeatsPerTable = maxSeatsPerTable;
        return c;
    }

    public int remainingTables() {
        return maxTables - reservedTables;
    }

    public void reserveOne() {
        if (remainingTables() < 1) {
            throw new IllegalStateException("남은 테이블이 없다: venue " + venueId + ", slot " + eventSlotId);
        }
        reservedTables++;
    }

    /** 운영자가 수용량을 늘린다 (식당 부족 예외 처리, 8.x 화면). */
    public void changeMaxTables(int maxTables) {
        if (maxTables < reservedTables) {
            throw new IllegalArgumentException("이미 배정된 테이블 수보다 작게 줄일 수 없다: " + reservedTables);
        }
        this.maxTables = maxTables;
    }
}
