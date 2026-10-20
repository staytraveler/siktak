package com.siktak.admin.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.admin.application.AdminOpsService;
import com.siktak.admin.application.AdminOpsService.HistoryRow;
import com.siktak.admin.application.AdminOpsService.PassRow;
import com.siktak.admin.application.AdminOpsService.PaymentRow;
import com.siktak.admin.application.AdminOpsService.TemplateRow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/** 운영자 결제·환불 목록, 이용권 사용 내역, 자동 메시지 템플릿·발송 이력 (PRD 11장). 권한은 /api/v1/admin/** OPERATOR 규칙. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminOpsController {

    private final AdminOpsService opsService;

    @GetMapping("/payments")
    public List<PaymentRow> payments(@RequestParam(required = false) String paymentStatus) {
        return opsService.payments(blankToNull(paymentStatus));
    }

    @GetMapping("/passes")
    public List<PassRow> passes() {
        return opsService.passes();
    }

    @GetMapping("/message-templates")
    public List<TemplateRow> templates() {
        return opsService.templates();
    }

    @PutMapping("/message-templates/{code}")
    public TemplateRow updateTemplate(@PathVariable String code, @Valid @RequestBody TemplateRequest request) {
        return opsService.updateTemplate(code, request.title(), request.body(), request.active());
    }

    @GetMapping("/message-history")
    public List<HistoryRow> history(@RequestParam(required = false) String sendStatus) {
        return opsService.history(blankToNull(sendStatus));
    }

    public record TemplateRequest(@NotBlank String title, @NotBlank String body, boolean active) {
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
