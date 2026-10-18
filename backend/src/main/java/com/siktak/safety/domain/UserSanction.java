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

/** 경고·참가 제한·영구 정지 기록 (SAF-02). */
@Getter
@Entity
@Table(name = "user_sanctions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSanction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sanction_type", nullable = false)
    private SanctionType sanctionType;

    @Column(nullable = false)
    private String reason;

    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    public static UserSanction of(Long userId, SanctionType type, String reason, Long reportId, Instant startsAt,
            Instant endsAt, Long operatorId) {
        UserSanction s = new UserSanction();
        s.userId = userId;
        s.sanctionType = type;
        s.reason = reason;
        s.reportId = reportId;
        s.startsAt = startsAt;
        s.endsAt = endsAt;
        s.operatorId = operatorId;
        return s;
    }
}
