package com.siktak.safety.domain;

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

/** 차단·신고 분리 관계 (SAF-01). 비공개 — 매칭 엔진만 읽고, 상대에게는 어떤 API로도 내보내지 않는다. */
@Getter
@Entity
@Table(name = "user_blocks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlock extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blocker_id", nullable = false)
    private Long blockerId;

    @Column(name = "blocked_id", nullable = false)
    private Long blockedId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BlockSource source;

    public static UserBlock of(Long blockerId, Long blockedId, BlockSource source) {
        UserBlock b = new UserBlock();
        b.blockerId = blockerId;
        b.blockedId = blockedId;
        b.source = source;
        return b;
    }
}
