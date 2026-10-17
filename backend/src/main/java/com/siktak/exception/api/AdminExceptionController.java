package com.siktak.exception.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.exception.application.ExceptionCaseService;
import com.siktak.exception.domain.CaseStatus;
import com.siktak.exception.domain.ExceptionCase;

import lombok.RequiredArgsConstructor;

/** 운영자 예외 처리함 조회 (PRD 11장). 처리 화면·처리 API는 8.x에서. */
@RestController
@RequestMapping("/api/v1/admin/exceptions")
@RequiredArgsConstructor
public class AdminExceptionController {

    private final ExceptionCaseService exceptionCaseService;

    /** {@code status}를 빼면 전체. */
    @GetMapping
    public List<ExceptionCaseResponse> list(@RequestParam(required = false) CaseStatus status) {
        return exceptionCaseService.list(status).stream().map(ExceptionCaseResponse::from).toList();
    }

    public record ExceptionCaseResponse(Long id, String caseType, String caseStatus, Long applicationId,
            Long diningTableId, String reason, Map<String, Object> detail, String caseKey, Instant createdAt,
            Long resolvedBy, String resolutionNote, Instant resolvedAt) {

        static ExceptionCaseResponse from(ExceptionCase c) {
            return new ExceptionCaseResponse(c.getId(), c.getCaseType().name(), c.getCaseStatus().name(),
                    c.getApplicationId(), c.getDiningTableId(), c.getReason(), c.getDetail(), c.getCaseKey(),
                    c.getCreatedAt(), c.getResolvedBy(), c.getResolutionNote(), c.getResolvedAt());
        }
    }
}
