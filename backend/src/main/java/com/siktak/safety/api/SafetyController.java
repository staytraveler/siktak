package com.siktak.safety.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.common.security.AuthUser;
import com.siktak.safety.application.SafetyService;
import com.siktak.safety.domain.ReportReason;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * 차단(SAF-01)·신고(SAF-01). 동석자는 {@code GET /me/applications/{id}/companions}의 {@code companionId}로 가리킨다
 * (지시서의 {@code userId} 대신 — 다른 참가자의 사용자 ID를 노출하지 않으려는 [추측] 변경, 완료 보고에 기록).
 * 응답에는 상대 정보도, 신고 내용도 담지 않는다.
 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class SafetyController {

    private final SafetyService safetyService;

    @PostMapping("/blocks")
    @ResponseStatus(HttpStatus.CREATED)
    public void block(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody BlockRequest request) {
        safetyService.block(user.id(), request.applicationId(), request.companionId());
    }

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportReceipt report(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ReportRequest request) {
        var report = safetyService.report(user.id(), request.applicationId(), request.companionId(),
                request.reasonType(), request.description());
        return new ReportReceipt(report.getId(), report.getReportStatus().name());
    }

    public record BlockRequest(@NotNull Long applicationId, @NotNull Long companionId) {
    }

    public record ReportRequest(@NotNull Long applicationId, @NotNull Long companionId,
            @NotNull ReportReason reasonType, @NotBlank @Size(max = 2000) String description) {
    }

    public record ReportReceipt(Long reportId, String reportStatus) {
    }
}
