package com.siktak.matching.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.siktak.common.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 매칭으로 만들어진 테이블 (erd.md dining_tables). 식당 배정(venue_id)은 5.x에서 채운다. */
@Getter
@Entity
@Table(name = "dining_tables")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiningTable extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_slot_id", nullable = false)
    private Long eventSlotId;

    @Column(name = "match_run_id", nullable = false)
    private Long matchRunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "table_status", nullable = false)
    private TableStatus tableStatus;

    @Column(name = "member_count", nullable = false)
    private int memberCount;

    @Column(name = "total_score")
    private BigDecimal totalScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_detail", nullable = false)
    private Map<String, Object> scoreDetail = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> summary = new HashMap<>();

    @Column(name = "venue_id")
    private Long venueId;

    @Column(name = "reservation_name")
    private String reservationName;

    @Column(name = "venue_assigned_at")
    private Instant venueAssignedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Version
    private Long version;

    public static DiningTable confirmed(Long eventSlotId, Long matchRunId, int memberCount,
            Map<String, Object> summary, Instant confirmedAt) {
        DiningTable table = new DiningTable();
        table.eventSlotId = eventSlotId;
        table.matchRunId = matchRunId;
        table.tableStatus = TableStatus.CONFIRMED;
        table.memberCount = memberCount;
        table.summary = new HashMap<>(summary);
        table.confirmedAt = confirmedAt;
        return table;
    }
}
