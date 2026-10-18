package com.siktak.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

import com.siktak.application.domain.Application;
import com.siktak.application.domain.PassType;
import com.siktak.application.domain.status.AttendanceStatus;
import com.siktak.application.domain.status.MatchStatus;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.exception.domain.CaseStatus;
import com.siktak.exception.domain.CaseType;
import com.siktak.exception.domain.ExceptionCase;
import com.siktak.feedback.application.AttendanceService;
import com.siktak.feedback.application.HistoryService;
import com.siktak.feedback.application.PersonPreferenceService;
import com.siktak.feedback.domain.Attendance;
import com.siktak.feedback.infra.AttendanceRepository;
import com.siktak.safety.application.SafetyService;
import com.siktak.safety.domain.ReportReason;
import com.siktak.safety.domain.ReportStatus;
import com.siktak.safety.domain.ResolveAction;
import com.siktak.user.domain.AccountStatus;

/**
 * 7.2 신고·차단·운영자 조치와 참가 이력·추천 (SAF-01·02, RET-01·02).
 * 시드 → J1 → J4(체크인) → J5(행사 종료) → user01이 동석자 신고 → 2회차(S4) → J6 → 두 사람 다른 테이블
 * → 운영자 RESTRICTION → 그 사용자는 3회차(S5) 후보에서 제외.
 */
@Tag("db")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class SafetyFlowIntegrationTest extends DemoScenarioDbTestSupport {

    @Autowired private AttendanceService attendanceService;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private PersonPreferenceService preferenceService;
    @Autowired private SafetyService safetyService;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    @Autowired private HistoryService historyService;

    @Test
    void 신고부터_참가_제한_매칭_제외까지_이어진다() {
        // ---- J1 → 체크인 → 행사 종료 (회차 DONE, 만남 이력 생성)
        clock.set(s1.getMatchRunAt());
        matchingService.runForSlot(s1.getId(), null);
        clock.set(s1.getStartsAt());
        List<Attendance> round1 = new ArrayList<>(attendanceRepository.findByEventSlotId(s1.getId()));
        round1.addAll(attendanceRepository.findByEventSlotId(s2.getId()));
        for (Attendance a : round1) {
            attendanceService.checkIn(a.getUserId(), a.getApplicationId());
        }
        clock.set(s1.getEndsAt());
        attendanceService.processEventEnd(s1.getId());
        attendanceService.processEventEnd(s2.getId());

        // ---- 참가 이력 (RET-01): 종료된 회차, 지역·식당·참석 상태, 피드백 전
        Application u1 = app.get(1);
        List<HistoryService.HistoryItem> history = historyService.history(u1.getUserId());
        assertThat(history).singleElement().satisfies(h -> {
            assertThat(h.eventSlotId()).isEqualTo(s1.getId());
            assertThat(h.attendanceStatus()).isEqualTo(AttendanceStatus.ATTENDED);
            assertThat(h.regionName()).isNotBlank();
            assertThat(h.venueName()).isNotBlank();
            assertThat(h.feedbackSubmitted()).isFalse();
        });

        // ---- 신고 (SAF-01): 동석자 1명. 남의 테이블 사람은 못 고른다
        List<PersonPreferenceService.Companion> companions = preferenceService.companions(u1.getUserId(), u1.getId());
        Long reportedCompanion = companions.get(0).companionId();
        Long reportedUserId = tableMemberRepository.findById(reportedCompanion).orElseThrow().getUserId();
        Application reportedApp = app.values().stream().filter(a -> a.getUserId().equals(reportedUserId)).findFirst()
                .orElseThrow();
        Long strangerCompanion = tableMemberRepository.findByApplicationIdAndActiveTrue(otherTableApp(u1).getId())
                .orElseThrow().getId();
        assertThatThrownBy(() -> safetyService.report(u1.getUserId(), u1.getId(), strangerCompanion,
                ReportReason.HARASSMENT, "x")).isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        assertThatThrownBy(() -> safetyService.block(app.get(2).getUserId(), u1.getId(), reportedCompanion))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        var report = safetyService.report(u1.getUserId(), u1.getId(), reportedCompanion, ReportReason.HARASSMENT,
                "불편한 언행이 있었습니다");
        // 두 번 보내도 1건·예외 1건·분리 1건 (멱등)
        assertThat(safetyService.report(u1.getUserId(), u1.getId(), reportedCompanion, ReportReason.HARASSMENT, "다시").getId())
                .isEqualTo(report.getId());
        assertThat(report.getReportStatus()).isEqualTo(ReportStatus.RECEIVED);
        assertThat(openCases(CaseType.SAFETY_REPORT)).singleElement()
                .satisfies(c -> assertThat(c.getCaseKey()).isEqualTo(SafetyService.reportCaseKey(report.getId())));
        assertThat(jdbc.queryForObject("""
                select count(*) from user_blocks where blocker_id = ? and blocked_id = ? and source = 'REPORT'
                """, Integer.class, u1.getUserId(), reportedUserId)).isEqualTo(1);
        // 사용자 차단도 같은 사람에게 해도 새로 만들지 않는다
        safetyService.block(u1.getUserId(), u1.getId(), reportedCompanion);
        assertThat(jdbc.queryForObject("select count(*) from user_blocks where blocker_id = ? and blocked_id = ?",
                Integer.class, u1.getUserId(), reportedUserId)).isEqualTo(1);
        // 피신고자 쪽에서는 아무것도 보이지 않는다 (동석자 이름뿐)
        assertThat(preferenceService.mine(reportedUserId, reportedApp.getId())).isEmpty();

        // ---- 2회차 투입 (S4) → J6: 신고한 사람과 된 사람은 다른 테이블
        // s5는 s4와 마감·매칭 시각이 같아 s4 실행 때 함께 처리된다 — 3회차 시나리오용으로 하루 늦춘다 (캐시된 엔티티를 비우고 다시 읽는다)
        jdbc.update("update event_slots set apply_deadline_at = apply_deadline_at + interval '1 day',"
                + " match_run_at = match_run_at + interval '1 day' where id = ?", s5.getId());
        entityManager.flush();
        entityManager.clear();
        s5 = eventSlotRepository.findById(s5.getId()).orElseThrow();
        String tag = UUID.randomUUID().toString().substring(0, 8);
        List<Long> round2Users = new ArrayList<>(
                attendanceRepository.findByEventSlotIdAndStatus(s1.getId(), AttendanceStatus.ATTENDED).stream()
                        .map(Attendance::getUserId).toList());
        round2Users.add(rawParticipant(tag, 25, 1996, "FEMALE"));
        round2Users.add(rawParticipant(tag, 26, 1998, "MALE"));
        for (Long userId : round2Users) {
            Application a = applicationService.submit(userId, List.of(s4.getId()), PassType.SINGLE);
            applicationService.approvePayment(userId, a.getId());
        }
        clock.set(s4.getMatchRunAt());
        matchingService.runForSlot(s4.getId(), null);
        Application reporterNext = appFor(u1.getUserId(), s4.getId());
        Application reportedNext = appFor(reportedUserId, s4.getId());
        assertThat(reporterNext.getMatchStatus()).isEqualTo(MatchStatus.CONFIRMED);
        assertThat(reportedNext.getMatchStatus()).isEqualTo(MatchStatus.CONFIRMED);
        assertThat(tableOf(reporterNext)).as("신고한 사람과 신고된 사람은 S4에서 다른 테이블")
                .isNotEqualTo(tableOf(reportedNext));

        // ---- 운영자 조치 (SAF-02): 참가 제한 → 계정 RESTRICTED, 예외 해결
        assertThatThrownBy(() -> safetyService.resolve(operatorId, report.getId(), ResolveAction.RESTRICTION, null, "x"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        assertThat(safetyService.listReports(true)).extracting(v -> v.report().getId()).contains(report.getId());
        safetyService.resolve(operatorId, report.getId(), ResolveAction.RESTRICTION, 30, "경고 후 재발");
        safetyService.resolve(operatorId, report.getId(), ResolveAction.PERMANENT_BAN, null, "재호출은 무시");
        assertThat(userRepository.findById(reportedUserId).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.RESTRICTED);
        assertThat(jdbc.queryForObject("select count(*) from user_sanctions where report_id = ?", Integer.class,
                report.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select sanction_type from user_sanctions where report_id = ? and ends_at is not null
                  and ends_at = starts_at + interval '30 days'
                """, String.class, report.getId())).isEqualTo("RESTRICTION");
        assertThat(safetyService.view(report.getId()).report().getReportStatus()).isEqualTo(ReportStatus.RESOLVED);
        ExceptionCase closed = exceptionCaseRepository.findByCaseKey(SafetyService.reportCaseKey(report.getId()))
                .orElseThrow();
        assertThat(closed.getCaseStatus()).isEqualTo(CaseStatus.RESOLVED);
        assertThat(openCases(CaseType.SAFETY_REPORT)).isEmpty();
        assertThat(safetyService.listReports(true)).extracting(v -> v.report().getId()).doesNotContain(report.getId());

        // ---- 다음 매칭(S5, 3회차): 제한된 사용자는 후보에서 빠진다
        clock.set(s4.getMatchRunAt().plus(Duration.ofMinutes(1)));
        List<Long> round3Users = new ArrayList<>(round2Users);
        for (Long userId : round3Users) {
            Application a = applicationService.submit(userId, List.of(s5.getId()), PassType.SINGLE);
            applicationService.approvePayment(userId, a.getId());
        }
        clock.set(s5.getMatchRunAt());
        matchingService.runForSlot(s5.getId(), null);
        Application restrictedApp = appFor(reportedUserId, s5.getId());
        assertThat(restrictedApp.getMatchStatus()).isNotEqualTo(MatchStatus.CONFIRMED);
        assertThat(tableMemberRepository.findByApplicationIdAndActiveTrue(restrictedApp.getId())).isEmpty();
        assertThat(appFor(u1.getUserId(), s5.getId()).getMatchStatus()).isEqualTo(MatchStatus.CONFIRMED);

        // ---- 다음 일정 추천 (RET-02): 이미 신청한 회차는 빠진다
        assertThat(historyService.recommendations(u1.getUserId())).extracting(HistoryService.Recommendation::eventSlotId)
                .doesNotContain(s4.getId(), s5.getId());
    }

    private Long tableOf(Application application) {
        return tableMemberRepository.findByApplicationIdAndActiveTrue(application.getId()).orElseThrow()
                .getDiningTableId();
    }

    private Application appFor(Long userId, Long slotId) {
        return applicationService.findMine(userId).stream().filter(a -> a.hasChosenSlot(slotId)).findFirst()
                .orElseThrow();
    }

    private Application otherTableApp(Application of) {
        Long own = tableMemberRepository.findByApplicationIdAndActiveTrue(of.getId()).orElseThrow().getDiningTableId();
        return app.values().stream()
                .filter(a -> tableMemberRepository.findByApplicationIdAndActiveTrue(a.getId())
                        .map(m -> !m.getDiningTableId().equals(own)).orElse(false))
                .findFirst().orElseThrow();
    }
}
