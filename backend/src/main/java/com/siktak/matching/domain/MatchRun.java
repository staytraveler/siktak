package com.siktak.matching.domain;

import java.time.Instant;
import java.time.LocalDate;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 매칭 실행 단위 기록 (erd.md match_runs). run_key가 중복 실행을 막는다. */
@Getter
@Entity
@Table(name = "match_runs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchRun extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_key", nullable = false, unique = true)
    private String runKey;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "algorithm_version", nullable = false)
    private String algorithmVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> params = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "run_status", nullable = false)
    private RunStatus runStatus;

    @Column(name = "candidate_count", nullable = false)
    private int candidateCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> summary = new HashMap<>();

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    public static MatchRun start(String runKey, LocalDate targetDate, String algorithmVersion,
            Map<String, Object> params, int candidateCount, Instant startedAt) {
        MatchRun run = new MatchRun();
        run.runKey = runKey;
        run.targetDate = targetDate;
        run.algorithmVersion = algorithmVersion;
        run.params = new HashMap<>(params);
        run.runStatus = RunStatus.RUNNING;
        run.candidateCount = candidateCount;
        run.startedAt = startedAt;
        return run;
    }

    public void complete(Map<String, Object> summary, Instant finishedAt) {
        this.summary = new HashMap<>(summary);
        this.runStatus = RunStatus.COMPLETED;
        this.finishedAt = finishedAt;
    }
}
