package com.siktak.safety.domain;

import java.time.Instant;

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

/** 안전 신고 (SAF-01). 내용은 신고자와 운영자만 본다. 피신고자에게는 절대 노출하지 않는다. */
@Getter
@Entity
@Table(name = "safety_reports")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SafetyReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Column(name = "reported_id", nullable = false)
    private Long reportedId;

    @Column(name = "dining_table_id")
    private Long diningTableId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", nullable = false)
    private ReportReason reasonType;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_status", nullable = false)
    private ReportStatus reportStatus;

    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_at")
    private Instant handledAt;

    public static SafetyReport receive(Long reporterId, Long reportedId, Long diningTableId, ReportReason reason,
            String description) {
        SafetyReport r = new SafetyReport();
        r.reporterId = reporterId;
        r.reportedId = reportedId;
        r.diningTableId = diningTableId;
        r.reasonType = reason;
        r.description = description;
        r.reportStatus = ReportStatus.RECEIVED;
        return r;
    }

    public boolean isClosed() {
        return reportStatus == ReportStatus.RESOLVED || reportStatus == ReportStatus.DISMISSED;
    }

    /** 이미 닫힌 신고면 바꾸지 않는다 (멱등). */
    public void close(ReportStatus status, Long operatorId, Instant at) {
        if (isClosed()) {
            return;
        }
        this.reportStatus = status;
        this.handledBy = operatorId;
        this.handledAt = at;
    }
}
