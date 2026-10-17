package com.siktak.application.domain;

import com.siktak.common.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 신청이 후보로 고른 회차. priority 1이 최우선 (matching.md MD7). */
@Getter
@Entity
@Table(name = "application_slot_choices")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationSlotChoice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id")
    private Application application;

    @Column(name = "event_slot_id", nullable = false)
    private Long eventSlotId;

    @Column(nullable = false)
    private int priority;

    ApplicationSlotChoice(Application application, Long eventSlotId, int priority) {
        this.application = application;
        this.eventSlotId = eventSlotId;
        this.priority = priority;
    }
}
