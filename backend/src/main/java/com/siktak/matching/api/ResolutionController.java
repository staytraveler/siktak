package com.siktak.matching.api;

import java.time.Instant;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.common.security.AuthUser;
import com.siktak.matching.application.ResolutionService;
import com.siktak.matching.application.ResolutionService.ResolutionView;
import com.siktak.matching.domain.ResolutionChoice;
import com.siktak.slot.api.SlotResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/** 매칭 실패 후 일정 이동·이용권 보관·환불 선택 (PRD STA-04~06, PAY-02·03). */
@RestController
@RequestMapping("/api/v1/me/applications/{applicationId}/resolution")
@RequiredArgsConstructor
public class ResolutionController {

    private final ResolutionService resolutionService;

    /** 추천 회차(이유 포함), 선택지 3개, 응답 기한. */
    @GetMapping
    public ResolutionResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long applicationId) {
        return ResolutionResponse.from(resolutionService.view(user.id(), applicationId));
    }

    /** 선택. TRANSFER면 slotId 필수(추천 회차 중 하나). 같은 선택을 다시 보내면 결과만 돌려준다. */
    @PostMapping
    public ResolutionResponse choose(@AuthenticationPrincipal AuthUser user, @PathVariable Long applicationId,
            @Valid @RequestBody ChooseRequest request) {
        return ResolutionResponse.from(
                resolutionService.choose(user.id(), applicationId, request.choice(), request.slotId()));
    }

    public record ChooseRequest(@NotNull ResolutionChoice choice, Long slotId) {
    }

    public record ResolutionResponse(
            Long applicationId,
            String matchStatus,
            String paymentStatus,
            String matchFailReason,
            String resolutionStatus,
            Instant respondBy,
            String choice,
            Long chosenSlotId,
            Long newApplicationId,
            Instant respondedAt,
            List<OfferedSlotResponse> offeredSlots,
            List<ChoiceResponse> choices) {

        static ResolutionResponse from(ResolutionView v) {
            var a = v.application();
            var r = v.resolution();
            return new ResolutionResponse(a.getId(), name(a.getMatchStatus()), name(a.getPaymentStatus()),
                    a.getMatchFailReason(), r.getResolutionStatus().name(), r.getRespondBy(), r.getChoice(),
                    r.getChosenSlotId(), r.getNewApplicationId(), r.getRespondedAt(),
                    v.offeredSlots().stream().map(o -> new OfferedSlotResponse(SlotResponse.from(o.slot()),
                            o.reasonCode(), o.reason(), o.available())).toList(),
                    v.choices().stream().map(c -> new ChoiceResponse(c.choice().name(), c.available(),
                            c.description())).toList());
        }

        private static String name(Enum<?> e) {
            return e == null ? null : e.name();
        }
    }

    public record OfferedSlotResponse(SlotResponse slot, String reasonCode, String reason, boolean available) {
    }

    public record ChoiceResponse(String choice, boolean available, String description) {
    }
}
