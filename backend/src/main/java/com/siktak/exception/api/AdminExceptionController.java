package com.siktak.exception.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.application.application.RefundService;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.common.security.AuthUser;
import com.siktak.exception.application.ExceptionCaseService;
import com.siktak.exception.domain.CaseStatus;
import com.siktak.exception.domain.CaseType;
import com.siktak.exception.domain.ExceptionCase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/** 운영자 예외 처리함 (PRD 11장). 처리 화면·나머지 처리 API는 8.x에서. */
@RestController
@RequestMapping("/api/v1/admin/exceptions")
@RequiredArgsConstructor
public class AdminExceptionController {

    private final ExceptionCaseService exceptionCaseService;
    private final RefundService refundService;

    /** {@code status}를 빼면 전체. */
    @GetMapping
    public List<ExceptionCaseResponse> list(@RequestParam(required = false) CaseStatus status) {
        return exceptionCaseService.list(status).stream().map(ExceptionCaseResponse::from).toList();
    }

    /** 환불 실패 재처리 (states.md P11). 결과 결제 상태를 돌려준다. */
    @PostMapping("/{caseId}/retry-refund")
    public RefundActionResponse retryRefund(@AuthenticationPrincipal AuthUser user, @PathVariable Long caseId) {
        Long applicationId = refundCaseApplication(caseId);
        return new RefundActionResponse(applicationId,
                refundService.retryByOperator(applicationId, user.id()).name());
    }

    /** PG 밖에서 환불을 마쳤을 때 수동 완료 (states.md P12, 사유 필수). */
    @PostMapping("/{caseId}/mark-refunded")
    public RefundActionResponse markRefunded(@AuthenticationPrincipal AuthUser user, @PathVariable Long caseId,
            @Valid @RequestBody MarkRefundedRequest request) {
        Long applicationId = refundCaseApplication(caseId);
        return new RefundActionResponse(applicationId,
                refundService.markRefundedByOperator(applicationId, user.id(), request.reason()).name());
    }

    private Long refundCaseApplication(Long caseId) {
        ExceptionCase c = exceptionCaseService.find(caseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "예외를 찾을 수 없습니다."));
        if (c.getCaseType() != CaseType.REFUND_FAILED || c.getApplicationId() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "환불 실패 예외가 아닙니다.");
        }
        return c.getApplicationId();
    }

    public record MarkRefundedRequest(@NotBlank String reason) {
    }

    public record RefundActionResponse(Long applicationId, String paymentStatus) {
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
