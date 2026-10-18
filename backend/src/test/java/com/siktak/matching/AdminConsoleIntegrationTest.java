package com.siktak.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

import com.siktak.admin.application.DashboardService;
import com.siktak.application.domain.status.AttendanceStatus;
import com.siktak.common.error.BusinessException;
import com.siktak.common.schedule.JobTrigger;
import com.siktak.common.schedule.ScheduledJobRegistry;
import com.siktak.exception.application.ExceptionCaseService;
import com.siktak.exception.domain.CaseStatus;
import com.siktak.exception.domain.CaseType;
import com.siktak.exception.domain.ExceptionCase;
import com.siktak.feedback.application.AttendanceService;
import com.siktak.feedback.domain.Attendance;
import com.siktak.matching.domain.DiningTable;
import com.siktak.notification.application.NotificationSendJobHandler;
import com.siktak.notification.application.NotificationService;
import com.siktak.notification.domain.Notification;
import com.siktak.notification.domain.NotificationStatus;
import com.siktak.notification.infra.InAppNotificationSender;
import com.siktak.venue.application.VenueCapacityService;

/** 8.1 운영자 어드민: 대시보드 집계, 예외 처리함 조치(식당 재배정·노쇼 T5·알림 재발송·수동 해결). */
@Tag("db")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class AdminConsoleIntegrationTest extends DemoScenarioDbTestSupport {

    @Autowired private DashboardService dashboardService;
    @Autowired private VenueCapacityService venueCapacityService;
    @Autowired private ExceptionCaseService exceptionCaseService;
    @Autowired private AttendanceService attendanceService;
    @Autowired private NotificationService notificationService;
    @Autowired private NotificationSendJobHandler sendJobHandler;
    @Autowired private InAppNotificationSender sender;
    @Autowired private ScheduledJobRegistry jobRegistry;

    @AfterEach
    void reset() {
        sender.setFailInjection(false);
        jobRegistry.replaceAll(List.of());
    }

    @Test
    void 대시보드는_회차별_현황과_처리할_예외를_센다() {
        runJ1();
        DashboardService.Dashboard d = dashboardService.load();

        DashboardService.SlotStat s1Stat = d.slots().stream().filter(s -> s.slotId().equals(s1.getId()))
                .findFirst().orElseThrow();
        DashboardService.SlotStat s2Stat = d.slots().stream().filter(s -> s.slotId().equals(s2.getId()))
                .findFirst().orElseThrow();
        // S1: 후보로 고른 신청 / 결제 완료 / 이 회차에 확정된 신청 (demo-scenario 4장)
        assertThat(s1Stat.applied()).isGreaterThan(s1Stat.confirmed());
        assertThat(s1Stat.confirmed()).isEqualTo(14);
        assertThat(s2Stat.confirmed()).isEqualTo(6);
        assertThat(d.totals().confirmed()).isGreaterThanOrEqualTo(20);

        // 식당 부족 테이블 1건이 처리할 예외로 올라온다
        assertThat(d.exceptions().byType().get(CaseType.VENUE_UNAVAILABLE)).isGreaterThanOrEqualTo(1);
        assertThat(d.exceptions().total()).isGreaterThanOrEqualTo(1);
        // 지표: 분모가 있으면 0~1 비율
        assertThat(d.metrics().autoConfirm().denominator()).isGreaterThanOrEqualTo(4);
        assertThat(d.metrics().autoConfirm().rate()).isBetween(0.0, 1.0);
        assertThat(d.metrics().autoRealloc().numerator()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void 식당_부족_예외는_수용량을_늘리고_사유와_함께_재배정하면_해결된다() {
        runJ1();
        DiningTable noVenue = diningTableRepository.findByEventSlotIdOrderByIdAsc(s1.getId()).stream()
                .filter(t -> !t.hasVenue()).findFirst().orElseThrow();

        var views = venueCapacityService.ofSlot(s1.getId());
        assertThat(views).extracting(VenueCapacityService.VenueCapacityView::venueName).contains(green.getName());
        assertThat(venueCapacityService.changeMaxTables(s1.getId(), green.getId(), 1).maxTables()).isEqualTo(1);
        // 이미 배정된 수보다 줄일 수는 없다
        assertThatThrownBy(() -> venueCapacityService.changeMaxTables(s1.getId(), oak.getId(), 0))
                .isInstanceOf(BusinessException.class);

        tableVenueService.assignVenue(noVenue.getId(), operatorId, "그린키친 1테이블 추가 확보");
        ExceptionCase c = exceptionCaseRepository
                .findByCaseKey(ExceptionCaseService.venueUnavailableKey(noVenue.getId())).orElseThrow();
        assertThat(c.getCaseStatus()).isEqualTo(CaseStatus.RESOLVED);
        assertThat(c.getResolvedBy()).isEqualTo(operatorId);
        assertThat(c.getResolvedAt()).isNotNull();
        assertThat(c.getResolutionNote()).contains("그린키친 1테이블 추가 확보");
    }

    @Test
    void 노쇼_후보는_확정하면_NO_SHOW로_바뀌고_해제하면_후보만_풀린다() {
        runJ1();
        clock.set(s1.getEndsAt());
        attendanceService.processEventEnd(s1.getId());
        Attendance first = attendanceService.ofSlot(s1.getId()).get(0);
        Attendance second = attendanceService.ofSlot(s1.getId()).get(1);
        assertThat(first.isNoShowCandidate()).isTrue();

        attendanceService.confirmNoShow(first.getId(), operatorId, "연락 두절");
        assertThat(first.getAttendanceStatus()).isEqualTo(AttendanceStatus.NO_SHOW);
        // 두 번 눌러도 같은 결과 (멱등)
        attendanceService.confirmNoShow(first.getId(), operatorId, "연락 두절");
        assertThat(first.getAttendanceStatus()).isEqualTo(AttendanceStatus.NO_SHOW);
        ExceptionCase confirmed = exceptionCaseRepository
                .findByCaseKey(AttendanceService.noShowCaseKey(first.getApplicationId())).orElseThrow();
        assertThat(confirmed.getCaseStatus()).isEqualTo(CaseStatus.RESOLVED);
        assertThat(confirmed.getResolutionNote()).contains("연락 두절");

        attendanceService.dismissNoShowCandidate(second.getId(), operatorId, "현장에서 확인됨");
        assertThat(second.isNoShowCandidate()).isFalse();
        assertThat(second.getAttendanceStatus()).isEqualTo(AttendanceStatus.SCHEDULED);
        assertThat(exceptionCaseRepository.findByCaseKey(AttendanceService.noShowCaseKey(second.getApplicationId()))
                .orElseThrow().getCaseStatus()).isEqualTo(CaseStatus.RESOLVED);
    }

    @Test
    void 발송_실패_알림은_운영자가_재발송하면_보내지고_예외가_해결된다() {
        sender.setFailInjection(true);
        Notification n = notificationService.enqueue(app.get(17).getUserId(), "REFUND_COMPLETED",
                Map.of("amount", "9,900원"), null, "TEST:resend");
        for (int i = 0; i < 3; i++) {
            clock.set(clock.instant().plus(Duration.ofMinutes(1)));
            sendJobHandler.execute(n.getId(), JobTrigger.tick());
        }
        assertThat(n.getSendStatus()).isEqualTo(NotificationStatus.FAILED);

        // 아직 고장이면 다시 실패하고 예외는 열린 채로
        notificationService.resendByOperator(n.getId(), operatorId);
        assertThat(n.getSendStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(exceptionCaseRepository.findByCaseKey(NotificationService.failedKey(n.getId())).orElseThrow()
                .getCaseStatus()).isEqualTo(CaseStatus.OPEN);

        sender.setFailInjection(false);
        notificationService.resendByOperator(n.getId(), operatorId);
        assertThat(n.getSendStatus()).isEqualTo(NotificationStatus.SENT);
        ExceptionCase c = exceptionCaseRepository.findByCaseKey(NotificationService.failedKey(n.getId()))
                .orElseThrow();
        assertThat(c.getCaseStatus()).isEqualTo(CaseStatus.RESOLVED);
        assertThat(c.getResolvedBy()).isEqualTo(operatorId);
    }

    @Test
    void 수동_해결은_사유가_없으면_거절한다() {
        ExceptionCase c = exceptionCaseService.open(CaseType.MATCH_CONFLICT, "TEST:manual", null, null, "조건 충돌",
                Map.of());
        assertThatThrownBy(() -> exceptionCaseService.resolveManually(c.getCaseKey(), operatorId, " "))
                .isInstanceOf(BusinessException.class);
        exceptionCaseService.resolveManually(c.getCaseKey(), operatorId, "직접 조정함");
        assertThat(c.getCaseStatus()).isEqualTo(CaseStatus.RESOLVED);
        assertThat(c.getResolutionNote()).isEqualTo("직접 조정함");
    }

    private void runJ1() {
        clock.set(s1.getMatchRunAt());
        matchingService.runForSlot(s1.getId(), null);
    }
}
