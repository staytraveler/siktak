package com.siktak.matching.domain;

import java.time.Instant;

import com.siktak.common.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 테이블 구성원. (application_id) WHERE active 부분 유니크로 이중 배치를 막는다. */
@Getter
@Entity
@Table(name = "table_members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TableMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dining_table_id", nullable = false)
    private Long diningTableId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    /** 취소·재배치로 테이블을 떠난다. */
    public void leave(Instant at) {
        this.active = false;
        this.leftAt = at;
    }

    public static TableMember join(Long diningTableId, Long applicationId, Long userId, Instant joinedAt) {
        TableMember member = new TableMember();
        member.diningTableId = diningTableId;
        member.applicationId = applicationId;
        member.userId = userId;
        member.joinedAt = joinedAt;
        return member;
    }
}
