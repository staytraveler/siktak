package com.siktak.safety.api;

import java.time.Instant;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.common.security.AuthUser;
import com.siktak.safety.application.SafetyService;
import com.siktak.safety.domain.ResolveAction;
import com.siktak.safety.domain.SafetyReport;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/** 운영자 신고 조회·조치 (SAF-02). 권한은 SecurityConfig의 {@code /api/v1/admin/**} OPERATOR 규칙. */
@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
public class AdminSafetyController {

    private final SafetyService safetyService;

    /** {@code open=true}면 미처리 신고만. */
    @GetMapping
    public List<ReportResponse> list(@RequestParam(defaultValue = "false") boolean open) {
        return safetyService.listReports(open).stream().map(ReportResponse::from).toList();
    }

    @PostMapping("/{reportId}/resolve")
    public ReportResponse resolve(@AuthenticationPrincipal AuthUser user, @PathVariable Long reportId,
            @Valid @RequestBody ResolveRequest request) {
        safetyService.resolve(user.id(), reportId, request.action(), request.days(), request.note());
        return ReportResponse.from(safetyService.view(reportId));
    }

    public record ResolveRequest(@NotNull ResolveAction action, Integer days, String note) {
    }

    public record ReportResponse(Long id, Long reporterId, String reporterName, Long reportedId, String reportedName,
            Long diningTableId, String reasonType, String description, String reportStatus, Long handledBy,
            Instant handledAt, Instant createdAt) {

        static ReportResponse from(SafetyService.ReportView v) {
            SafetyReport r = v.report();
            return new ReportResponse(r.getId(), r.getReporterId(), v.reporterName(), r.getReportedId(),
                    v.reportedName(), r.getDiningTableId(), r.getReasonType().name(), r.getDescription(),
                    r.getReportStatus().name(), r.getHandledBy(), r.getHandledAt(), r.getCreatedAt());
        }
    }
}
