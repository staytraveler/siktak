package com.siktak.feedback.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.application.application.ApplicationService;
import com.siktak.application.domain.status.ActorType;
import com.siktak.application.domain.status.AttendanceStatus;
import com.siktak.application.domain.status.StatusHistory;
import com.siktak.application.domain.status.Transition;
import com.siktak.application.domain.status.TransitionContext;
import com.siktak.application.infra.StatusHistoryRepository;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.common.schedule.ScheduledJob;
import com.siktak.common.schedule.ScheduledJobRegistry;
import com.siktak.exception.application.ExceptionCaseService;
import com.siktak.exception.domain.CaseType;
import com.siktak.feedback.domain.Attendance;
import com.siktak.feedback.domain.EventEndedEvent;
import com.siktak.feedback.infra.AttendanceRepository;
import com.siktak.feedback.infra.UserEncounterWriter;
import com.siktak.matching.domain.DiningTable;
import com.siktak.matching.domain.TableMemberConfirmedEvent;
import com.siktak.matching.domain.TableMemberDroppedEvent;
import com.siktak.matching.domain.TableMemberMovedEvent;
import com.siktak.matching.infra.DiningTableRepository;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.domain.SlotStatus;
import com.siktak.slot.infra.EventSlotRepository;

/**
 * 참석 상태 관리 (FDB-01, states.md 5장 T1·T2) 와 행사 종료 처리 (EVENT_END, MAT-06).
 * 참석 상태는 신청의 세 축과 별개 테이블이라 StatusTransitionService를 거치지 않고 여기서 전이표를 직접 검사하고 이력을 남긴다.
 */
@Service
public class AttendanceService {

    public static final String EVENT_END_JOB = "EVENT_END";
    static final String TARGET_ATTENDANCE = "ATTENDANCE";

    private final AttendanceRepository attendanceRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final ApplicationService applicationService;
    private final DiningTableRepository diningTableRepository;
    private final EventSlotRepository eventSlotRepository;
    private final UserEncounterWriter encounterWriter;
    private final ExceptionCaseService exceptionCaseService;
    private final ScheduledJobRegistry jobRegistry;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration checkinOpen;

    public AttendanceService(AttendanceRepository attendanceRepository, StatusHistoryRepository statusHistoryRepository,
            ApplicationService applicationService, DiningTableRepository diningTableRepository,
            EventSlotRepository eventSlotRepository, UserEncounterWriter encounterWriter,
            ExceptionCaseService exceptionCaseService, ScheduledJobRegistry jobRegistry,
            ApplicationEventPublisher events, Clock clock,
            @Value("${siktak.policy.checkin-open-minutes:30}") long checkinOpenMinutes) {
        this.attendanceRepository = attendanceRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.applicationService = applicationService;
        this.diningTableRepository = diningTableRepository;
        this.eventSlotRepository = eventSlotRepository;
        this.encounterWriter = encounterWriter;
        this.exceptionCaseService = exceptionCaseService;
        this.jobRegistry = jobRegistry;
        this.events = events;
        this.clock = clock;
        this.checkinOpen = Duration.ofMinutes(checkinOpenMinutes);
    }

    /** T1: 매칭 확정(M3·M5) 시 참석 예정을 만든다. 같은 신청에는 한 번만 (멱등). 행사 종료 작업도 함께 예약한다. */
    @EventListener
    @Transactional
    public void onTableMemberConfirmed(TableMemberConfirmedEvent event) {
        if (attendanceRepository.existsByApplicationId(event.applicationId())) {
            return;
        }
        Attendance attendance = attendanceRepository.save(
                Attendance.scheduled(event.applicationId(), event.diningTableId(), event.userId()));
        recordHistory(attendance, Transition.T1, null, AttendanceStatus.SCHEDULED,
                TransitionContext.system("매칭 확정, 참석 예정"));
        EventSlot slot = slotOf(attendance);
        // 커밋 뒤 등록된다. 같은 회차를 여러 번 등록해도 하나로 합쳐진다.
        jobRegistry.schedule(new ScheduledJob(EVENT_END_JOB, slot.getId(), slot.getEndsAt()));
    }

    /** 확정 후 취소 재배치로 다른 확정 테이블로 옮겨졌다 — 참석 정보의 테이블을 맞춘다. */
    @EventListener
    @Transactional
    public void onTableMemberMoved(TableMemberMovedEvent event) {
        attendanceRepository.findByApplicationId(event.applicationId()).ifPresent(a -> a.moveToTable(event.toTableId()));
    }

    /** 확정 후 취소 재배치에서 들어갈 테이블이 없어 빠졌다 — 참석 예정을 없애 노쇼 후보로 잡히지 않게 한다. */
    @EventListener
    @Transactional
    public void onTableMemberDropped(TableMemberDroppedEvent event) {
        attendanceRepository.findByApplicationId(event.applicationId()).ifPresent(attendanceRepository::delete);
    }

    /**
     * T3·T4: 참가자가 참석을 취소한다. 행사 2일 전까지면 사전 취소(T3), 그 뒤면 당일·제한 시점 이후 취소(T4).
     * 사전 취소의 환불·크레딧 복원, 후속 재배치는 CancellationService가 맡는다.
     */
    @Transactional
    public Attendance cancel(Long userId, Long applicationId, boolean early) {
        applicationService.getOwned(userId, applicationId);
        Attendance attendance = attendanceRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "참석 예정 정보가 없습니다."));
        if (attendance.getAttendanceStatus() != AttendanceStatus.SCHEDULED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "이미 처리된 참석입니다.");
        }
        AttendanceStatus to = early ? AttendanceStatus.CANCELED_EARLY : AttendanceStatus.CANCELED_LATE;
        attendance.markCanceled(to, Instant.now(clock));
        recordHistory(attendance, early ? Transition.T3 : Transition.T4, AttendanceStatus.SCHEDULED, to,
                TransitionContext.of(ActorType.PARTICIPANT, userId,
                        early ? "참석 사전 취소 (행사 2일 전까지)" : "참석 취소 (행사 2일 전 이후)"));
        return attendance;
    }

    /** T2: 앱 체크인. 행사 시작 {@code checkin-open-minutes}분 전부터 종료 시각까지. 이미 체크인했으면 그대로 (멱등). */
    @Transactional
    public Attendance checkIn(Long userId, Long applicationId) {
        applicationService.getOwned(userId, applicationId);
        Attendance attendance = attendanceRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "참석 예정 정보가 없습니다. 테이블이 확정된 신청만 체크인할 수 있어요."));
        if (attendance.getAttendanceStatus() == AttendanceStatus.ATTENDED) {
            return attendance;
        }
        if (attendance.getAttendanceStatus() != AttendanceStatus.SCHEDULED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "체크인할 수 없는 참석 상태입니다.");
        }
        EventSlot slot = slotOf(attendance);
        Instant now = Instant.now(clock);
        if (now.isBefore(opensAt(slot)) || now.isAfter(slot.getEndsAt())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "체크인은 행사 시작 " + checkinOpen.toMinutes() + "분 전부터 종료까지 할 수 있어요.");
        }
        attendance.markAttended(now);
        recordHistory(attendance, Transition.T2, AttendanceStatus.SCHEDULED, AttendanceStatus.ATTENDED,
                TransitionContext.of(ActorType.PARTICIPANT, userId, "앱 체크인"));
        return attendance;
    }

    /** 신청의 참석 상태 (참석 정보가 없으면 null). */
    @Transactional(readOnly = true)
    public AttendanceStatus statusOf(Long applicationId) {
        return attendanceRepository.findByApplicationId(applicationId).map(Attendance::getAttendanceStatus).orElse(null);
    }

    @Transactional(readOnly = true)
    public AttendanceView view(Long userId, Long applicationId) {
        applicationService.getOwned(userId, applicationId);
        Attendance attendance = attendanceRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "참석 예정 정보가 없습니다."));
        EventSlot slot = slotOf(attendance);
        Instant now = Instant.now(clock);
        boolean canCheckIn = attendance.getAttendanceStatus() == AttendanceStatus.SCHEDULED
                && !now.isBefore(opensAt(slot)) && !now.isAfter(slot.getEndsAt());
        return new AttendanceView(attendance, opensAt(slot), slot.getEndsAt(), canCheckIn);
    }

    @Transactional(readOnly = true)
    public List<Attendance> ofSlot(Long slotId) {
        return attendanceRepository.findByEventSlotId(slotId);
    }

    /**
     * EVENT_END: 행사 종료 시각에 한 번 실행한다. 미체크인은 노쇼 후보로 표시하고 예외 처리함에 올리며(T5는 운영자 확정),
     * 같은 테이블 참석자 쌍마다 만남 이력을 남기고, 종료 이벤트를 발행한다. 회차가 DONE이면 이미 처리된 것이다 (멱등).
     */
    @Transactional
    public void processEventEnd(long slotId) {
        EventSlot slot = eventSlotRepository.findById(slotId).orElse(null);
        Instant now = Instant.now(clock);
        if (slot == null || slot.getSlotStatus() != SlotStatus.MATCHED || now.isBefore(slot.getEndsAt())) {
            return;
        }
        List<Attendance> all = attendanceRepository.findByEventSlotId(slotId);
        List<Long> noShowUserIds = new ArrayList<>();
        for (Attendance a : all) {
            if (a.getAttendanceStatus() != AttendanceStatus.SCHEDULED) {
                continue;
            }
            a.markNoShowCandidate();
            noShowUserIds.add(a.getUserId());
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("eventSlotId", slotId);
            detail.put("userId", a.getUserId());
            exceptionCaseService.open(CaseType.NO_SHOW_CANDIDATE,
                    noShowCaseKey(a.getApplicationId()), a.getApplicationId(),
                    a.getDiningTableId(), "행사 종료까지 체크인하지 않았습니다. 노쇼 여부를 확인해주세요.", detail);
        }
        Map<Long, List<Attendance>> attendedByTable = all.stream()
                .filter(a -> a.getAttendanceStatus() == AttendanceStatus.ATTENDED)
                .collect(Collectors.groupingBy(Attendance::getDiningTableId, LinkedHashMap::new, Collectors.toList()));
        List<Long> attendedUserIds = new ArrayList<>();
        attendedByTable.forEach((tableId, members) -> {
            for (int i = 0; i < members.size(); i++) {
                attendedUserIds.add(members.get(i).getUserId());
                for (int j = i + 1; j < members.size(); j++) {
                    encounterWriter.record(members.get(i).getUserId(), members.get(j).getUserId(), tableId,
                            slot.getStartsAt());
                }
            }
        });
        slot.markDone();
        events.publishEvent(new EventEndedEvent(slotId, attendedUserIds, noShowUserIds));
    }

    /** T5: 운영자가 노쇼 후보를 노쇼로 확정한다. 이미 노쇼면 그대로 (멱등). 예외 처리함의 후보 건도 함께 해결한다. */
    @Transactional
    public Attendance confirmNoShow(Long attendanceId, Long operatorId, String reason) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "참석 정보를 찾을 수 없습니다."));
        if (attendance.getAttendanceStatus() == AttendanceStatus.NO_SHOW) {
            return attendance;
        }
        if (attendance.getAttendanceStatus() != AttendanceStatus.SCHEDULED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "예정 상태의 참석만 노쇼로 확정할 수 있습니다.");
        }
        attendance.markNoShow();
        recordHistory(attendance, Transition.T5, AttendanceStatus.SCHEDULED, AttendanceStatus.NO_SHOW,
                TransitionContext.of(ActorType.OPERATOR, operatorId, reason));
        exceptionCaseService.resolve(noShowCaseKey(attendance.getApplicationId()), operatorId, "노쇼 확정: " + reason);
        return attendance;
    }

    /** 노쇼 후보를 해제한다 (실제로는 참석했거나 사정이 있는 경우). 참석 상태는 바꾸지 않고 예외만 해결한다. */
    @Transactional
    public Attendance dismissNoShowCandidate(Long attendanceId, Long operatorId, String reason) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "참석 정보를 찾을 수 없습니다."));
        attendance.clearNoShowCandidate();
        exceptionCaseService.resolve(noShowCaseKey(attendance.getApplicationId()), operatorId, "노쇼 후보 해제: " + reason);
        return attendance;
    }

    /** 신청의 참석 정보 ID (예외 처리함에서 노쇼 후보 건을 처리할 때). */
    @Transactional(readOnly = true)
    public Long findIdByApplication(Long applicationId) {
        return attendanceRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "참석 정보를 찾을 수 없습니다.")).getId();
    }

    public static String noShowCaseKey(Long applicationId) {
        return "NO_SHOW_CANDIDATE:application-" + applicationId;
    }

    /** 기동 시 catch-up 대상: 확정은 됐지만 종료 처리가 안 된 회차. */
    @Transactional(readOnly = true)
    public List<ScheduledJob> pendingEventEnds() {
        return eventSlotRepository.findBySlotStatusOrderByStartsAtAscIdAsc(SlotStatus.MATCHED).stream()
                .map(s -> new ScheduledJob(EVENT_END_JOB, s.getId(), s.getEndsAt()))
                .toList();
    }

    private Instant opensAt(EventSlot slot) {
        return slot.getStartsAt().minus(checkinOpen);
    }

    private EventSlot slotOf(Attendance attendance) {
        DiningTable table = diningTableRepository.findById(attendance.getDiningTableId()).orElseThrow();
        return eventSlotRepository.findById(table.getEventSlotId()).orElseThrow();
    }

    private void recordHistory(Attendance attendance, Transition transition, AttendanceStatus from, AttendanceStatus to,
            TransitionContext context) {
        statusHistoryRepository.save(StatusHistory.record(TARGET_ATTENDANCE, attendance.getId(), transition, from, to,
                context, context.reason()));
    }

    public record AttendanceView(Attendance attendance, Instant checkInOpensAt, Instant checkInClosesAt,
            boolean canCheckIn) {
    }
}
