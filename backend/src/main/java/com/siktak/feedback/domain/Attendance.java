package com.siktak.feedback.domain;

import java.time.Instant;

import com.siktak.application.domain.status.AttendanceStatus;
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
 * 참석 (erd.md 3-7, states.md 5장). 신청당 1건, 매칭 확정 시 SCHEDULED로 생긴다.
 * 상태 변경은 AttendanceService만 한다 (전이표 검사·이력 기록).
 */
@Getter
@Entity
@Table(name = "attendances")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attendance extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, unique = true)
    private Long applicationId;

    @Column(name = "dining_table_id", nullable = false)
    private Long diningTableId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", nullable = false)
    private AttendanceStatus attendanceStatus;

    @Column(name = "no_show_candidate", nullable = false)
    private boolean noShowCandidate;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    public static Attendance scheduled(Long applicationId, Long diningTableId, Long userId) {
        Attendance attendance = new Attendance();
        attendance.applicationId = applicationId;
        attendance.diningTableId = diningTableId;
        attendance.userId = userId;
        attendance.attendanceStatus = AttendanceStatus.SCHEDULED;
        return attendance;
    }

    /** 사전·당일 취소 (T3·T4). 전이표 검사는 AttendanceService가 한다. */
    public void markCanceled(AttendanceStatus status, Instant at) {
        this.attendanceStatus = status;
        this.canceledAt = at;
        this.noShowCandidate = false;
    }

    /** 재배치로 다른 테이블로 옮겨졌다. */
    public void moveToTable(Long diningTableId) {
        this.diningTableId = diningTableId;
    }

    /** 전이표 검사는 AttendanceService가 한다. */
    public void markAttended(Instant at) {
        this.attendanceStatus = AttendanceStatus.ATTENDED;
        this.checkedInAt = at;
        this.noShowCandidate = false;
    }

    public void markNoShowCandidate() {
        this.noShowCandidate = true;
    }

    /** T5: 운영자가 노쇼를 확정한다. 전이표 검사는 AttendanceService가 한다. */
    public void markNoShow() {
        this.attendanceStatus = AttendanceStatus.NO_SHOW;
    }

    /** 노쇼 후보 표시를 풀어 준다 (운영자 해제). 참석 상태는 그대로다. */
    public void clearNoShowCandidate() {
        this.noShowCandidate = false;
    }
}
