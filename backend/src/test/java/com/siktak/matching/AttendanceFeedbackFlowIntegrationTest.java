package com.siktak.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
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
import com.siktak.common.schedule.JobTrigger;
import com.siktak.common.schedule.ScheduledJob;
import com.siktak.common.schedule.ScheduledJobRegistry;
import com.siktak.common.schedule.ScheduledJobRunner;
import com.siktak.exception.domain.CaseType;
import com.siktak.feedback.application.AttendanceService;
import com.siktak.feedback.application.EventEndJobHandler;
import com.siktak.feedback.application.FeedbackService;
import com.siktak.feedback.application.PersonPreferenceService;
import com.siktak.feedback.domain.Attendance;
import com.siktak.feedback.domain.PreferenceType;
import com.siktak.feedback.infra.AttendanceRepository;
import com.siktak.slot.domain.SlotStatus;

/**
 * 7.1 체크인·노쇼 후보·만족도·사람별 선호·만남 이력, 그리고 그 선호가 다음 매칭에 반영되는지 (PRD 18장 10~11번).
 * 시드 → J1 → J4(체크인, user13 제외) → J5(EVENT_END) → 피드백·AVOID → 2회차 투입 → J6 → AVOID 쌍은 다른 테이블.
 */
@Tag("db")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class AttendanceFeedbackFlowIntegrationTest extends DemoScenarioDbTestSupport {

    @Autowired private AttendanceService attendanceService;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private FeedbackService feedbackService;
    @Autowired private PersonPreferenceService preferenceService;
    @Autowired private EventEndJobHandler eventEndJobHandler;
    @Autowired private ScheduledJobRegistry jobRegistry;
    @Autowired private ScheduledJobRunner jobRunner;

    @AfterEach
    void clearJobs() {
        jobRegistry.replaceAll(List.of());
    }

    @Test
    void 체크인부터_AVOID_반영까지_이어진다() {
        // ---- J1: 매칭 확정 → 확정자 전원 SCHEDULED (T1)
        clock.set(s1.getMatchRunAt());
        matchingService.runForSlot(s1.getId(), null);
        List<Attendance> round1 = bothSlots();
        assertThat(round1).isNotEmpty().allSatisfy(a -> {
            assertThat(a.getAttendanceStatus()).isEqualTo(AttendanceStatus.SCHEDULED);
            assertThat(a.isNoShowCandidate()).isFalse();
        });
        assertThat(attendanceCodes(app.get(1))).containsExactly("T1");
        // 확정되지 않은 사람(user14 대기, user16 미결제)은 참석 기록이 없다
        assertThat(attendanceRepository.findByApplicationId(app.get(14).getId())).isEmpty();
        assertThat(attendanceRepository.findByApplicationId(app.get(16).getId())).isEmpty();

        // ---- 체크인: 행사 30분 전 이전에는 못 하고, 남의 신청으로는 못 한다
        Application u1 = app.get(1);
        clock.set(s1.getStartsAt().minus(Duration.ofMinutes(31)));
        assertThatThrownBy(() -> attendanceService.checkIn(u1.getUserId(), u1.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
        assertThat(attendanceService.view(u1.getUserId(), u1.getId()).canCheckIn()).isFalse();
        clock.set(s1.getStartsAt().minus(Duration.ofMinutes(30)));
        assertThatThrownBy(() -> attendanceService.checkIn(app.get(2).getUserId(), u1.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        // ---- J4: user13을 뺀 전원 체크인 (T2), 다시 눌러도 그대로
        clock.set(s1.getStartsAt());
        long expectedAttended = 0;
        for (Attendance a : round1) {
            if (a.getApplicationId().equals(app.get(13).getId())) {
                continue;
            }
            attendanceService.checkIn(a.getUserId(), a.getApplicationId());
            attendanceService.checkIn(a.getUserId(), a.getApplicationId());
            expectedAttended++;
        }
        assertThat(attendanceStatus(u1)).isEqualTo(AttendanceStatus.ATTENDED);
        assertThat(attendanceCodes(u1)).containsExactly("T1", "T2");
        assertThat(attendanceStatus(app.get(13))).isEqualTo(AttendanceStatus.SCHEDULED);
        // 체크인 전에는 피드백도 사람별 선호도 남길 수 없다
        Application u13 = app.get(13);
        assertThatThrownBy(() -> feedbackService.submit(u13.getUserId(), u13.getId(), 4, 4, 4, 4, 8, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        assertThatThrownBy(() -> preferenceService.replace(u13.getUserId(), u13.getId(), List.of()))
                .isInstanceOf(BusinessException.class);

        // ---- J5: EVENT_END 작업 — 두 회차가 같은 시각에 끝난다
        clock.set(s1.getEndsAt());
        List<ScheduledJob> mine = eventEndJobHandler.loadPending().stream()
                .filter(j -> j.targetId() == s1.getId() || j.targetId() == s2.getId()).toList();
        assertThat(mine).extracting(ScheduledJob::dueAt).containsOnly(s1.getEndsAt());
        jobRegistry.replaceAll(mine);
        assertThat(jobRunner.runDue(JobTrigger.tick())).isEqualTo(2);

        assertThat(attendanceRepository.findByEventSlotIdAndStatus(s1.getId(), AttendanceStatus.SCHEDULED))
                .extracting(Attendance::getApplicationId).containsExactly(u13.getId());
        assertThat(bothSlots().stream().filter(Attendance::isNoShowCandidate))
                .extracting(Attendance::getApplicationId).containsExactly(u13.getId());
        assertThat(openCases(CaseType.NO_SHOW_CANDIDATE)).singleElement()
                .satisfies(c -> assertThat(c.getApplicationId()).isEqualTo(u13.getId()));
        assertThat(eventSlotRepository.findById(s1.getId()).orElseThrow().getSlotStatus()).isEqualTo(SlotStatus.DONE);
        assertThat(bothSlots().stream().filter(a -> a.getAttendanceStatus() == AttendanceStatus.ATTENDED).count())
                .isEqualTo(expectedAttended);

        // 만남 이력: 같은 테이블 참석자 쌍마다 1건, 다시 실행해도 늘지 않는다
        int expectedPairs = pairsAttended();
        assertThat(expectedPairs).isPositive();
        assertThat(encounterCount()).isEqualTo(expectedPairs);
        attendanceService.processEventEnd(s1.getId());
        attendanceService.processEventEnd(s2.getId());
        assertThat(encounterCount()).isEqualTo(expectedPairs);
        assertThat(openCases(CaseType.NO_SHOW_CANDIDATE)).hasSize(1);

        // ---- 피드백: 체크인자만, 한 번만 (FDB-02·03)
        feedbackService.submit(u1.getUserId(), u1.getId(), 5, 4, 3, 4, 9, "좋았어요");
        assertThatThrownBy(() -> feedbackService.submit(u1.getUserId(), u1.getId(), 5, 5, 5, 5, 10, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
        var saved = feedbackService.findMine(u1.getUserId()).get(0);
        assertThat(saved.getRejoinIntent()).isEqualTo(4);
        assertThat(saved.getRecommendScore()).isEqualTo(9);

        // ---- 사람별 선호 (FDB-04): user01이 같은 테이블 동석자 1명을 AVOID
        List<PersonPreferenceService.Companion> companions = preferenceService.companions(u1.getUserId(), u1.getId());
        assertThat(companions).isNotEmpty().allSatisfy(c -> assertThat(c.name()).isNotBlank());
        PersonPreferenceService.Companion avoided = companions.get(0);
        assertThat(preferenceService.replace(u1.getUserId(), u1.getId(),
                List.of(new PersonPreferenceService.Choice(avoided.companionId(), PreferenceType.AVOID))))
                .containsExactly(new PersonPreferenceService.Choice(avoided.companionId(), PreferenceType.AVOID));
        // 같은 요청을 다시 보내도 1건 (멱등)
        preferenceService.replace(u1.getUserId(), u1.getId(),
                List.of(new PersonPreferenceService.Choice(avoided.companionId(), PreferenceType.AVOID)));
        assertThat(jdbc.queryForObject("select count(*) from person_preferences where from_user_id = ?",
                Integer.class, u1.getUserId())).isEqualTo(1);
        // 다른 테이블 사람은 고를 수 없다
        Application stranger = otherTableApplication(u1);
        Long strangerCompanionId = tableMemberRepository.findByApplicationIdAndActiveTrue(stranger.getId())
                .orElseThrow().getId();
        assertThatThrownBy(() -> preferenceService.replace(u1.getUserId(), u1.getId(),
                List.of(new PersonPreferenceService.Choice(strangerCompanionId, PreferenceType.AVOID))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        // 상대에게는 보이지 않는다: 상대의 조회·동석자 목록 어디에도 user01의 선택이 없다
        Application avoidedApp = applicationOfCompanion(avoided.companionId());
        assertThat(preferenceService.mine(avoidedApp.getUserId(), avoidedApp.getId())).isEmpty();
        assertThat(preferenceService.companions(avoidedApp.getUserId(), avoidedApp.getId()))
                .extracting(PersonPreferenceService.Companion::name).doesNotContainNull();

        // ---- 피드백 일괄(시연) 흐름: 체크인자 중 피드백 없는 사람에게 기본 피드백
        for (Attendance a : bothSlots()) {
            if (a.getAttendanceStatus() == AttendanceStatus.ATTENDED && !feedbackService.hasFeedback(a.getApplicationId())) {
                feedbackService.submit(a.getUserId(), a.getApplicationId(), 4, 4, 4, 4, 8, "(데모) 기본 피드백");
            }
        }
        assertThat(jdbc.queryForObject("select count(*) from feedbacks where application_id in (?, ?)",
                Integer.class, u1.getId(), avoidedApp.getId())).isEqualTo(2);

        // ---- 2회차 투입: 1회차 S1 참석자 전원 + user25·26이 S4에 신청 → J6
        String tag = UUID.randomUUID().toString().substring(0, 8);
        List<Long> round2Users = attendanceRepository.findByEventSlotIdAndStatus(s1.getId(), AttendanceStatus.ATTENDED)
                .stream().map(Attendance::getUserId).collect(Collectors.toCollection(java.util.ArrayList::new));
        round2Users.add(rawParticipant(tag, 25, 1996, "FEMALE"));
        round2Users.add(rawParticipant(tag, 26, 1998, "MALE"));
        for (Long userId : round2Users) {
            Application a = applicationService.submit(userId, List.of(s4.getId()), PassType.SINGLE);
            applicationService.approvePayment(userId, a.getId());
        }
        clock.set(s4.getMatchRunAt());
        matchingService.runForSlot(s4.getId(), null);

        Application u1Next = s4Application(u1.getUserId());
        Application avoidedNext = s4Application(avoidedApp.getUserId());
        assertThat(u1Next.getMatchStatus()).isEqualTo(MatchStatus.CONFIRMED);
        assertThat(avoidedNext.getMatchStatus()).isEqualTo(MatchStatus.CONFIRMED);
        Long t1 = tableMemberRepository.findByApplicationIdAndActiveTrue(u1Next.getId()).orElseThrow().getDiningTableId();
        Long t2 = tableMemberRepository.findByApplicationIdAndActiveTrue(avoidedNext.getId()).orElseThrow()
                .getDiningTableId();
        assertThat(t1).as("AVOID 쌍은 S4에서 다른 테이블").isNotEqualTo(t2);
        // 2회차 확정자도 참석 예정이 생긴다 (S4 행사 종료 작업 예약 대상)
        assertThat(attendanceRepository.findByEventSlotIdAndStatus(s4.getId(), AttendanceStatus.SCHEDULED)).isNotEmpty();
    }

    // ------------------------------------------------------------------ 도우미

    private List<Attendance> bothSlots() {
        List<Attendance> all = new java.util.ArrayList<>(attendanceRepository.findByEventSlotId(s1.getId()));
        all.addAll(attendanceRepository.findByEventSlotId(s2.getId()));
        return all;
    }

    private AttendanceStatus attendanceStatus(Application application) {
        return attendanceRepository.findByApplicationId(application.getId()).orElseThrow().getAttendanceStatus();
    }

    private List<String> attendanceCodes(Application application) {
        Long attendanceId = attendanceRepository.findByApplicationId(application.getId()).orElseThrow().getId();
        return statusHistoryRepository.findByTargetTypeAndTargetIdOrderByIdAsc("ATTENDANCE", attendanceId).stream()
                .map(h -> h.getTransitionCode()).toList();
    }

    private int pairsAttended() {
        return bothSlots().stream().filter(a -> a.getAttendanceStatus() == AttendanceStatus.ATTENDED)
                .collect(Collectors.groupingBy(Attendance::getDiningTableId, Collectors.counting())).values().stream()
                .mapToInt(n -> (int) (n * (n - 1) / 2)).sum();
    }

    private int encounterCount() {
        List<Long> tableIds = bothSlots().stream().map(Attendance::getDiningTableId).distinct().toList();
        return tableIds.stream().mapToInt(id -> jdbc.queryForObject(
                "select count(*) from user_encounters where dining_table_id = ?", Integer.class, id)).sum();
    }

    private Application otherTableApplication(Application of) {
        Long own = tableMemberRepository.findByApplicationIdAndActiveTrue(of.getId()).orElseThrow().getDiningTableId();
        return bothSlots().stream().filter(a -> !a.getDiningTableId().equals(own)).findFirst()
                .map(a -> app.values().stream().filter(x -> x.getId().equals(a.getApplicationId())).findFirst().orElseThrow())
                .orElseThrow();
    }

    private Application applicationOfCompanion(Long companionId) {
        Long applicationId = tableMemberRepository.findById(companionId).orElseThrow().getApplicationId();
        return app.values().stream().filter(a -> a.getId().equals(applicationId)).findFirst().orElseThrow();
    }

    private Application s4Application(Long userId) {
        return applicationService.findMine(userId).stream().filter(a -> a.hasChosenSlot(s4.getId())).findFirst()
                .orElseThrow();
    }
}
