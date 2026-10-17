package com.siktak.matching.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.application.application.StatusTransitionService;
import com.siktak.application.domain.Application;
import com.siktak.application.domain.status.ActorType;
import com.siktak.application.domain.status.ApplicationStatus;
import com.siktak.application.domain.status.MatchStatus;
import com.siktak.application.domain.status.PaymentStatus;
import com.siktak.application.domain.status.Transition;
import com.siktak.application.domain.status.TransitionContext;
import com.siktak.application.infra.ApplicationRepository;
import com.siktak.chat.application.ChatService;
import com.siktak.chat.domain.ChatMessage;
import com.siktak.chat.domain.ChatRoom;
import com.siktak.common.config.ClockConfig;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.matching.domain.DiningTable;
import com.siktak.matching.domain.MatchDecision;
import com.siktak.matching.domain.MatchPlacementLog;
import com.siktak.matching.domain.MatchRun;
import com.siktak.matching.domain.TableMember;
import com.siktak.matching.infra.DiningTableRepository;
import com.siktak.matching.infra.MatchPlacementLogRepository;
import com.siktak.matching.infra.MatchRunRepository;
import com.siktak.matching.infra.TableMemberRepository;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.infra.EventSlotRepository;

import lombok.RequiredArgsConstructor;

/**
 * 회차 매칭 실행 (MAT-01 가짜 버전). 같은 회차·같은 알고리즘은 run_key로 한 번만 실행된다 (PRD 7.3 멱등).
 * <p>[2.3] 스케줄러 대신 운영자 버튼으로 실행한다. 전이 M2의 주체는 표대로 SCHEDULER로 기록하고,
 * 실제로 누른 운영자는 actor_id·reason에 남긴다. 스케줄러는 2.4·4.5에서 만든다.
 */
@Service
@RequiredArgsConstructor
public class MatchingService {

    private static final ZoneId ZONE = ClockConfig.ZONE;

    private final TableMatcher tableMatcher;
    private final ApplicationRepository applicationRepository;
    private final EventSlotRepository eventSlotRepository;
    private final MatchRunRepository matchRunRepository;
    private final DiningTableRepository diningTableRepository;
    private final TableMemberRepository tableMemberRepository;
    private final MatchPlacementLogRepository placementLogRepository;
    private final StatusTransitionService transitionService;
    private final ChatService chatService;
    private final EngineMatchingService engineMatchingService;
    private final Clock clock;

    /** {@code v2}(기본): 매칭 엔진, {@code simple}: 2.3 가짜 순차 매칭 (데모 직전 비상용으로 남겨둔다). */
    @Value("${siktak.matching.engine:v2}")
    private String engineMode;

    /**
     * 회차 매칭 실행. 스케줄러와 운영자 버튼이 모두 이 경로로 들어온다.
     * v2는 매칭 시각이 도래한 회차를 함께 실행하고, 이미 매칭된 회차면 그 실행을 돌려준다.
     */
    @Transactional
    public MatchRun runForSlot(Long slotId, Long operatorId) {
        if (!"simple".equals(engineMode)) {
            return engineMatchingService.runForSlot(slotId, operatorId);
        }
        EventSlot slot = eventSlotRepository.findById(slotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "회차를 찾을 수 없습니다."));
        String runKey = "slot-" + slotId + ":" + tableMatcher.algorithmVersion();
        var existing = matchRunRepository.findByRunKey(runKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        List<Application> candidates = applicationRepository.findCandidatesForSlot(slotId, MatchStatus.WAITING)
                .stream()
                .filter(a -> a.getApplicationStatus() == ApplicationStatus.SUBMITTED
                        && a.getPaymentStatus() == PaymentStatus.PAID)
                .toList();
        Instant now = Instant.now(clock);
        // operatorId가 null이면 스케줄러 자동 실행 (2.4 시간 기반 작업 실행기)
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("slotId", slotId);
        params.put("trigger", operatorId == null ? "SCHEDULER" : "OPERATOR_BUTTON");
        params.put("operatorId", operatorId);
        MatchRun run = matchRunRepository.save(MatchRun.start(runKey,
                slot.getStartsAt().atZone(ZONE).toLocalDate(), tableMatcher.algorithmVersion(),
                params, candidates.size(), now));

        TableMatcher.Result result = tableMatcher.match(candidates.stream()
                .map(a -> new TableMatcher.Candidate(a.getId(), a.getUserId(), a.getSubmittedAt()))
                .toList());

        Map<Long, Application> byId = candidates.stream()
                .collect(Collectors.toMap(Application::getId, Function.identity()));
        TransitionContext scheduler = new TransitionContext(ActorType.SCHEDULER, operatorId,
                operatorId == null ? "회차 매칭 마감 시각 도래 (자동 실행)" : "회차 매칭 실행 (운영자가 앞당겨 실행)",
                run.getId());
        TransitionContext system = new TransitionContext(ActorType.SYSTEM, null, "필수 조건 충족, 자동 확정", run.getId());

        for (List<Long> group : result.groups()) {
            DiningTable table = diningTableRepository.save(DiningTable.confirmed(slotId, run.getId(), group.size(),
                    Map.of("matcher", tableMatcher.algorithmVersion(), "note", "신청 순서 배치 (가짜 매칭)"), now));
            for (Long applicationId : group) {
                Application application = byId.get(applicationId);
                transitionService.apply(application, Transition.M2, scheduler);
                transitionService.apply(application, Transition.M3, system);
                application.assignSlot(slotId);
                tableMemberRepository.save(TableMember.join(table.getId(), applicationId, application.getUserId(), now));
                placementLogRepository.save(MatchPlacementLog.of(run.getId(), applicationId, MatchDecision.CONFIRMED,
                        table.getId(), "SIMPLE_SEQUENTIAL",
                        "신청 순서대로 " + group.size() + "명 테이블에 배치되어 확정됐습니다 (가짜 매칭)."));
            }
            ChatRoom room = chatService.openForTable(table.getId(),
                    group.stream().map(id -> byId.get(id).getUserId()).toList());
            chatService.postSystemMessage(room.getId(), ChatMessage.MessageType.NOTICE,
                    "테이블이 확정됐어요. 식당과 시간 안내는 곧 이 방에 올라옵니다.",
                    "table-" + table.getId() + ":confirmed", null);
        }

        if (!result.groups().isEmpty()) {
            slot.markMatched();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("tables", result.groups().size());
        summary.put("confirmed", result.groups().stream().mapToInt(List::size).sum());
        // [2.3] 미배치자는 상태를 바꾸지 않고 WAITING으로 둔다. 재배치·대안 제시(M4~M6)는 4.x·6.x에서 구현.
        summary.put("unplaced", result.unplaced().size());
        summary.put("unplacedApplicationIds", result.unplaced());
        run.complete(summary, Instant.now(clock));
        return run;
    }

    @Transactional(readOnly = true)
    public List<DiningTable> tablesOf(Long slotId) {
        return diningTableRepository.findByEventSlotIdOrderByIdAsc(slotId);
    }

    @Transactional(readOnly = true)
    public List<TableMember> membersOf(Long tableId) {
        return tableMemberRepository.findByDiningTableIdAndActiveTrue(tableId);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<TableMember> activeMembership(Long applicationId) {
        return tableMemberRepository.findByApplicationIdAndActiveTrue(applicationId);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<DiningTable> findTable(Long tableId) {
        return diningTableRepository.findById(tableId);
    }
}
