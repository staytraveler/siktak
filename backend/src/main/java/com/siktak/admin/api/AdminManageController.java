package com.siktak.admin.api;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.admin.application.AdminReportService;
import com.siktak.admin.application.AdminReportService.ApplicantRow;
import com.siktak.admin.application.AdminReportService.FeedbackSummary;
import com.siktak.admin.application.MatchingSettingsService;
import com.siktak.admin.application.MatchingSettingsService.MatchingSettings;
import com.siktak.common.security.AuthUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/** 운영자 설정·신청자 목록·피드백 요약·CSV (PRD 11장·16장, MAT-15). 권한은 {@code /api/v1/admin/**} OPERATOR 규칙. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminManageController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final MatchingSettingsService settingsService;
    private final AdminReportService reportService;

    @GetMapping("/settings/matching")
    public MatchingSettings matchingSettings() {
        return settingsService.get();
    }

    /** 다음 매칭 실행부터 적용된다. 가중치 합은 100이어야 한다. */
    @PutMapping("/settings/matching")
    public MatchingSettings updateMatchingSettings(@AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody MatchingSettingsRequest request) {
        return settingsService.update(request.weights(), request.minScore(), user.id());
    }

    public record MatchingSettingsRequest(@NotNull Map<String, Double> weights, @NotNull Double minScore) {
    }

    @GetMapping("/applicants")
    public List<ApplicantRow> applicants(@RequestParam(required = false) Long slotId) {
        return reportService.applicants(slotId);
    }

    @GetMapping("/feedback-summary")
    public FeedbackSummary feedbackSummary(@RequestParam(required = false) Long slotId) {
        return reportService.feedbackSummary(slotId);
    }

    @GetMapping("/exports/applicants.csv")
    public ResponseEntity<byte[]> applicantsCsv(@RequestParam(required = false) Long slotId) {
        return csv("applicants.csv", reportService.applicantsCsv(slotId));
    }

    @GetMapping("/exports/tables.csv")
    public ResponseEntity<byte[]> tablesCsv(@RequestParam(required = false) Long slotId) {
        return csv("tables.csv", reportService.tablesCsv(slotId));
    }

    private static ResponseEntity<byte[]> csv(String filename, String body) {
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body.getBytes(StandardCharsets.UTF_8));
    }
}
