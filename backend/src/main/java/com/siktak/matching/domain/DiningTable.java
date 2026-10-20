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

/** 매칭으로 만들어진 테이블 (erd.md dining_tables). 식당은 확정 직후 자동 배정한다 (4.4). */
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

    /** 매칭 엔진(v2)이 확정한 테이블. 점수 상세와 PRD 7.2 요약을 함께 저장한다. */
    public static DiningTable confirmedByEngine(Long eventSlotId, Long matchRunId, int memberCount,
            BigDecimal totalScore, Map<String, Object> scoreDetail, Map<String, Object> summary, Instant confirmedAt) {
        DiningTable table = confirmed(eventSlotId, matchRunId, memberCount, summary, confirmedAt);
        table.totalScore = totalScore;
        table.scoreDetail = new HashMap<>(scoreDetail);
        return table;
    }

    /** 취소·재배치로 구성이 바뀐 뒤 인원·점수·요약을 다시 기록한다 (확정 후 취소, matching.md 6장). */
    public void updateComposition(int memberCount, BigDecimal totalScore, Map<String, Object> scoreDetail,
            Map<String, Object> summary) {
        this.memberCount = memberCount;
        this.totalScore = totalScore;
        this.scoreDetail = new HashMap<>(scoreDetail);
        this.summary = new HashMap<>(summary);
    }

    /** 4명 미만이 되어 해체한다. */
    public void dissolve() {
        this.tableStatus = TableStatus.DISSOLVED;
        this.memberCount = 0;
    }

    /** 예약명은 처음 정한 값을 계속 쓴다 (재배정해도 바뀌지 않음). */
    public void proposeReservationName(String name) {
        if (this.reservationName == null) {
            this.reservationName = name;
        }
    }

    public boolean hasVenue() {
        return venueId != null;
    }

    public void assignVenue(Long venueId, Instant at) {
        if (this.venueId != null) {
            throw new IllegalStateException("이미 식당이 배정된 테이블이다: " + id);
        }
        this.venueId = venueId;
        this.venueAssignedAt = at;
    }
}
