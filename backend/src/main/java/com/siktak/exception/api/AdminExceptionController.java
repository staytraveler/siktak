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
import com.siktak.feedback.application.AttendanceService;
import com.siktak.matching.application.TableVenueService;
import com.siktak.notification.application.NotificationService;
import com.siktak.exception.domain.CaseStatus;
import com.siktak.exception.domain.CaseType;
import com.siktak.exception.domain.ExceptionCase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/** 운영자 예외 처리함 (PRD 11장). 목록과 유형별 조치. 처리 사유는 예외 기록(처리자·시각 포함)에 남는다. */
@RestController
@RequestMapping("/api/v1/admin/exceptions")
@RequiredArgsConstructor
public class AdminExceptionController {

    private final ExceptionCaseService exceptionCaseService;
    private final RefundService refundService;
    private final TableVenueService tableVenueService;
    private final AttendanceService attendanceService;
    private final NotificationService notificationService;

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

    /** 식당 부족: 수용량을 늘린 뒤 재배정한다 (수용량은 PUT /slots/{id}/venue-capacities/{venueId}). */
    @PostMapping("/{caseId}/assign-venue")
    public ActionResponse assignVenue(@AuthenticationPrincipal AuthUser user, @PathVariable Long caseId,
            @Valid @RequestBody ReasonRequest request) {
        ExceptionCase c = caseOf(caseId, CaseType.VENUE_UNAVAILABLE);
        tableVenueService.assignVenue(c.getDiningTableId(), user.id(), request.reason());
        return actionResult(caseId);
    }

    /** 노쇼 후보를 노쇼로 확정한다 (T5). */
    @PostMapping("/{caseId}/confirm-no-show")
    public ActionResponse confirmNoShow(@AuthenticationPrincipal AuthUser user, @PathVariable Long caseId,
            @Valid @RequestBody ReasonRequest request) {
        attendanceService.confirmNoShow(attendanceIdOf(caseId), user.id(), request.reason());
        return actionResult(caseId);
    }

    /** 노쇼 후보를 해제한다. */
    @PostMapping("/{caseId}/dismiss-no-show")
    public ActionResponse dismissNoShow(@AuthenticationPrincipal AuthUser user, @PathVariable Long caseId,
            @Valid @RequestBody ReasonRequest request) {
        attendanceService.dismissNoShowCandidate(attendanceIdOf(caseId), user.id(), request.reason());
        return actionResult(caseId);
    }

    /** 알림 재발송. 성공하면 예외가 해결되고, 다시 실패하면 OPEN으로 남는다. */
    @PostMapping("/{caseId}/resend-notification")
    public ActionResponse resendNotification(@AuthenticationPrincipal AuthUser user, @PathVariable Long caseId) {
        ExceptionCase c = caseOf(caseId, CaseType.NOTIFICATION_FAILED);
        Object notificationId = c.getDetail() == null ? null : c.getDetail().get("notificationId");
        if (!(notificationId instanceof Number n)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "알림 정보가 없는 예외입니다.");
        }
        notificationService.resendByOperator(n.longValue(), user.id());
        return actionResult(caseId);
    }

    /** 자동 조치가 없는 유형(조건 충돌·데이터 누락·결제 오류 등)을 운영자가 직접 처리한 뒤 닫는다 (사유 필수). */
    @PostMapping("/{caseId}/resolve")
    public ActionResponse resolve(@AuthenticationPrincipal AuthUser user, @PathVariable Long caseId,
            @Valid @RequestBody ReasonRequest request) {
        ExceptionCase c = caseOf(caseId, null);
        exceptionCaseService.resolveManually(c.getCaseKey(), user.id(), request.reason());
        return actionResult(caseId);
    }

    private ExceptionCase caseOf(Long caseId, CaseType expected) {
        ExceptionCase c = exceptionCaseService.find(caseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "예외를 찾을 수 없습니다."));
        if (expected != null && c.getCaseType() != expected) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, expected + " 예외가 아닙니다.");
        }
        return c;
    }

    private Long attendanceIdOf(Long caseId) {
        ExceptionCase c = caseOf(caseId, CaseType.NO_SHOW_CANDIDATE);
        return attendanceService.findIdByApplication(c.getApplicationId());
    }

    private ActionResponse actionResult(Long caseId) {
        ExceptionCase c = caseOf(caseId, null);
        return new ActionResponse(c.getId(), c.getCaseStatus().name());
    }

    public record ReasonRequest(@NotBlank String reason) {
    }

    public record ActionResponse(Long caseId, String caseStatus) {
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
