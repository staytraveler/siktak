package com.siktak.matching.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.application.domain.Application;
import com.siktak.application.infra.ApplicationRepository;
import com.siktak.matching.application.MatchQueryService;
import com.siktak.matching.domain.MatchPlacementLog;
import com.siktak.matching.domain.MatchRun;
import com.siktak.user.domain.User;
import com.siktak.user.infra.UserRepository;

import lombok.RequiredArgsConstructor;

/** 운영자 자동 매칭 모니터: 실행 기록과 참가자별 배치 사유 (PRD 11장, MAT-12). */
@RestController
@RequestMapping("/api/v1/admin/match-runs")
@RequiredArgsConstructor
public class AdminMatchRunController {

    private final MatchQueryService queryService;
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;

    @GetMapping
    public List<RunResponse> list() {
        return queryService.runs().stream().map(RunResponse::from).toList();
    }

    @GetMapping("/{runId}")
    public RunDetailResponse detail(@PathVariable Long runId) {
        MatchRun run = queryService.run(runId);
        List<MatchPlacementLog> logs = queryService.logsOfRun(runId);
        Map<Long, Application> applications = applicationRepository
                .findAllById(logs.stream().map(MatchPlacementLog::getApplicationId).distinct().toList()).stream()
                .collect(Collectors.toMap(Application::getId, Function.identity()));
        Map<Long, User> users = userRepository
                .findAllById(applications.values().stream().map(Application::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return new RunDetailResponse(RunResponse.from(run),
                queryService.tablesOfRun(runId).stream().map(t -> new TableBrief(t.getId(), t.getEventSlotId(),
                        t.getTableStatus().name(), t.getMemberCount(), t.getVenueId() != null)).toList(),
                logs.stream().map(l -> {
                    Application a = applications.get(l.getApplicationId());
                    User u = a == null ? null : users.get(a.getUserId());
                    return LogResponse.from(l, u == null ? null : u.getLoginId(), u == null ? null : u.getName());
                }).toList());
    }

    public record RunResponse(Long id, String runKey, String algorithmVersion, String runStatus, int candidateCount,
            Map<String, Object> params, Map<String, Object> summary, Instant startedAt, Instant finishedAt) {

        static RunResponse from(MatchRun r) {
            return new RunResponse(r.getId(), r.getRunKey(), r.getAlgorithmVersion(), r.getRunStatus().name(),
                    r.getCandidateCount(), r.getParams(), r.getSummary(), r.getStartedAt(), r.getFinishedAt());
        }
    }

    public record RunDetailResponse(RunResponse run, List<TableBrief> tables, List<LogResponse> placementLogs) {
    }

    public record TableBrief(Long id, Long slotId, String tableStatus, int memberCount, boolean venueAssigned) {
    }

    public record LogResponse(Long id, Long applicationId, String decision, Long fromSlotId, Long toSlotId,
            Long toTableId, Integer reallocStep, String reasonCode, String causeCode, String reasonText,
            String loginId, String userName) {

        static LogResponse from(MatchPlacementLog l, String loginId, String userName) {
            return new LogResponse(l.getId(), l.getApplicationId(), l.getDecision().name(), l.getFromSlotId(),
                    l.getToSlotId(), l.getToTableId(), l.getReallocStep(), l.getReasonCode(), l.getCauseCode(),
                    l.getReasonText(), loginId, userName);
        }
    }
}
