package com.siktak.application.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.application.application.ApplicationService;
import com.siktak.application.domain.Application;
import com.siktak.application.domain.PassType;
import com.siktak.common.security.AuthUser;
import com.siktak.slot.api.SlotResponse;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.infra.EventSlotRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/** 참가자 신청·결제·상태 조회 (APP-02·03, PAY-01, STA-01). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;
    private final EventSlotRepository eventSlotRepository;

    /** 신청 제출. slotIds는 선택 순서 = 우선순위. */
    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse submit(@AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody SubmitApplicationRequest request) {
        return toResponses(List.of(applicationService.submit(user.id(), request.slotIds(), request.passType())))
                .get(0);
    }

    /** [모의 결제] PRD PAY-01이 해커톤에서 허용한 모의 승인. 실결제(PAY-04)는 PaymentGateway 구현 교체로 붙인다. */
    @PostMapping("/applications/{applicationId}/payments/mock-approve")
    public ApplicationResponse approveMock(@AuthenticationPrincipal AuthUser user, @PathVariable Long applicationId) {
        return toResponses(List.of(applicationService.approvePayment(user.id(), applicationId))).get(0);
    }

    /** 내 신청 목록 — 신청·결제·매칭 상태를 각각 내려준다 (STA-01). */
    @GetMapping("/me/applications")
    public List<ApplicationResponse> mine(@AuthenticationPrincipal AuthUser user) {
        return toResponses(applicationService.findMine(user.id()));
    }

    private List<ApplicationResponse> toResponses(List<Application> applications) {
        List<Long> slotIds = applications.stream()
                .flatMap(a -> a.getSlotChoices().stream().map(c -> c.getEventSlotId()))
                .distinct().toList();
        Map<Long, EventSlot> slots = eventSlotRepository.findByIdIn(slotIds).stream()
                .collect(Collectors.toMap(EventSlot::getId, Function.identity()));
        return applications.stream().map(a -> ApplicationResponse.from(a, slots)).toList();
    }

    public record SubmitApplicationRequest(@NotEmpty List<@NotNull Long> slotIds, @NotNull PassType passType) {
    }

    public record ApplicationResponse(
            Long id,
            String passType,
            String applicationStatus,
            String paymentStatus,
            String matchStatus,
            Long assignedSlotId,
            Instant paymentDueAt,
            Instant submittedAt,
            String matchFailReason,
            List<SlotChoiceResponse> slotChoices) {

        static ApplicationResponse from(Application a, Map<Long, EventSlot> slots) {
            return new ApplicationResponse(a.getId(), a.getPassType().name(),
                    name(a.getApplicationStatus()), name(a.getPaymentStatus()), name(a.getMatchStatus()),
                    a.getAssignedSlotId(), a.getPaymentDueAt(), a.getSubmittedAt(), a.getMatchFailReason(),
                    a.getSlotChoicesByPriority().stream()
                            .map(c -> new SlotChoiceResponse(c.getPriority(),
                                    slots.containsKey(c.getEventSlotId())
                                            ? SlotResponse.from(slots.get(c.getEventSlotId())) : null))
                            .toList());
        }

        private static String name(Enum<?> e) {
            return e == null ? null : e.name();
        }
    }

    public record SlotChoiceResponse(int priority, SlotResponse slot) {
    }
}
