package com.siktak.matching.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.matching.application.MatchQueryService;
import com.siktak.matching.domain.MatchPlacementLog;
import com.siktak.matching.domain.MatchRun;

import lombok.RequiredArgsConstructor;

/** 운영자 자동 매칭 모니터: 실행 기록과 참가자별 배치 사유 (PRD 11장, MAT-12). */
@RestController
@RequestMapping("/api/v1/admin/match-runs")
@RequiredArgsConstructor
public class AdminMatchRunController {

    private final MatchQueryService queryService;

    @GetMapping
    public List<RunResponse> list() {
        return queryService.runs().stream().map(RunResponse::from).toList();
    }

    @GetMapping("/{runId}")
    public RunDetailResponse detail(@PathVariable Long runId) {
        MatchRun run = queryService.run(runId);
        return new RunDetailResponse(RunResponse.from(run),
                queryService.tablesOfRun(runId).stream().map(t -> t.getId()).toList(),
                queryService.logsOfRun(runId).stream().map(LogResponse::from).toList());
    }

    public record RunResponse(Long id, String runKey, String algorithmVersion, String runStatus, int candidateCount,
            Map<String, Object> params, Map<String, Object> summary, Instant startedAt, Instant finishedAt) {

        static RunResponse from(MatchRun r) {
            return new RunResponse(r.getId(), r.getRunKey(), r.getAlgorithmVersion(), r.getRunStatus().name(),
                    r.getCandidateCount(), r.getParams(), r.getSummary(), r.getStartedAt(), r.getFinishedAt());
        }
    }

    public record RunDetailResponse(RunResponse run, List<Long> tableIds, List<LogResponse> placementLogs) {
    }

    public record LogResponse(Long id, Long applicationId, String decision, Long fromSlotId, Long toSlotId,
            Long toTableId, Integer reallocStep, String reasonCode, String causeCode, String reasonText) {

        static LogResponse from(MatchPlacementLog l) {
            return new LogResponse(l.getId(), l.getApplicationId(), l.getDecision().name(), l.getFromSlotId(),
                    l.getToSlotId(), l.getToTableId(), l.getReallocStep(), l.getReasonCode(), l.getCauseCode(),
                    l.getReasonText());
        }
    }
}
