package com.siktak.exception.domain;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자동 처리되지 않아 운영자가 봐야 하는 예외 (erd.md exception_cases, PRD MAT-13·15장).
 * {@code case_key}가 같은 예외는 한 번만 생긴다.
 */
@Getter
@Entity
@Table(name = "exception_cases")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExceptionCase extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "case_type", nullable = false)
    private CaseType caseType;

    @Enumerated(EnumType.STRING)
    @Column(name = "case_status", nullable = false)
    private CaseStatus caseStatus;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "dining_table_id")
    private Long diningTableId;

    @Column(name = "ref_type")
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(nullable = false)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> detail;

    @Column(name = "case_key", nullable = false, unique = true)
    private String caseKey;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public static ExceptionCase open(CaseType caseType, String caseKey, Long applicationId, Long diningTableId,
            String reason, Map<String, Object> detail) {
        ExceptionCase c = new ExceptionCase();
        c.caseType = caseType;
        c.caseStatus = CaseStatus.OPEN;
        c.caseKey = caseKey;
        c.applicationId = applicationId;
        c.diningTableId = diningTableId;
        c.reason = reason;
        c.detail = detail == null ? null : new HashMap<>(detail);
        return c;
    }

    /** 이미 해결된 예외면 아무것도 바꾸지 않는다 (멱등). */
    public void resolve(Long operatorId, String note, Instant at) {
        if (caseStatus == CaseStatus.RESOLVED) {
            return;
        }
        this.caseStatus = CaseStatus.RESOLVED;
        this.resolvedBy = operatorId;
        this.resolutionNote = note;
        this.resolvedAt = at;
    }
}
