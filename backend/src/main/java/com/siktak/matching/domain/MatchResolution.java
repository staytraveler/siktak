package com.siktak.matching.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
 * 매칭 실패 후 참가자의 선택 대기 (erd.md match_resolutions, PRD STA-03·04, states.md M6~M9).
 * 신청 하나에 하나만 생긴다. 선택 처리(이동·보관·환불)는 6.x에서.
 */
@Getter
@Entity
@Table(name = "match_resolutions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchResolution extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, unique = true)
    private Long applicationId;

    /** 추천 순서 그대로. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "offered_slot_ids", nullable = false)
    private List<Long> offeredSlotIds = new ArrayList<>();

    private String choice;

    @Column(name = "chosen_slot_id")
    private Long chosenSlotId;

    @Column(name = "new_application_id")
    private Long newApplicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_status", nullable = false)
    private ResolutionStatus resolutionStatus;

    /** 미응답 시 보관 처리 기준 (원래 행사일, D6). */
    @Column(name = "respond_by", nullable = false)
    private Instant respondBy;

    @Column(name = "responded_at")
    private Instant respondedAt;

    public static MatchResolution offer(Long applicationId, List<Long> offeredSlotIds, Instant respondBy) {
        MatchResolution r = new MatchResolution();
        r.applicationId = applicationId;
        r.offeredSlotIds = new ArrayList<>(offeredSlotIds);
        r.resolutionStatus = ResolutionStatus.OFFERED;
        r.respondBy = respondBy;
        return r;
    }
}
