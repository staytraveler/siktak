package com.siktak.matching.domain;

import com.siktak.common.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 참가자 단위 배치 사유 (erd.md match_placement_logs, 설명 가능성 PRD 7.2). */
@Getter
@Entity
@Table(name = "match_placement_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchPlacementLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_run_id", nullable = false)
    private Long matchRunId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchDecision decision;

    @Column(name = "from_table_id")
    private Long fromTableId;

    @Column(name = "to_table_id")
    private Long toTableId;

    @Column(name = "realloc_step")
    private Integer reallocStep;

    @Column(name = "reason_code", nullable = false)
    private String reasonCode;

    @Column(name = "reason_text", nullable = false)
    private String reasonText;

    public static MatchPlacementLog of(Long matchRunId, Long applicationId, MatchDecision decision,
            Long toTableId, String reasonCode, String reasonText) {
        MatchPlacementLog log = new MatchPlacementLog();
        log.matchRunId = matchRunId;
        log.applicationId = applicationId;
        log.decision = decision;
        log.toTableId = toTableId;
        log.reasonCode = reasonCode;
        log.reasonText = reasonText;
        return log;
    }
}
