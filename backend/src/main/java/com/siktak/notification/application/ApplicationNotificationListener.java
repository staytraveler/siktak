package com.siktak.notification.application;

import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.siktak.application.domain.Application;
import com.siktak.application.domain.ApplicationSlotChoice;
import com.siktak.application.domain.status.ApplicationTransitioned;
import com.siktak.application.domain.status.Transition;
import com.siktak.application.infra.ApplicationRepository;
import com.siktak.matching.domain.MatchFailureRecorded;
import com.siktak.matching.infra.MatchResolutionRepository;
import com.siktak.payment.infra.PaymentRepository;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.infra.EventSlotRepository;

import lombok.RequiredArgsConstructor;

/**
 * 신청 상태 변화를 참가자 알림으로 바꾼다 (PRD 14장).
 * 상태를 바꾼 트랜잭션 안에서 동기로 처리해, 상태 변경과 알림이 함께 저장된다 (decisions.md 5.2).
 * 알림 키는 (템플릿, 신청)당 1개라 같은 전이가 반복돼도 한 번만 나간다.
 * <p>매칭 확정(MATCH_CONFIRMED)은 식당 배정 뒤에 문구가 완성되므로 여기가 아니라
 * {@code TableGuidanceService}가, 매칭 실패(MATCH_FAILED)는 사유 기록 뒤 {@link MatchFailureRecorded}로 보낸다.
 */
@Component
@RequiredArgsConstructor
public class ApplicationNotificationListener {

    private final NotificationService notificationService;
    private final ApplicationRepository applicationRepository;
    private final EventSlotRepository eventSlotRepository;
    private final PaymentRepository paymentRepository;
    private final MatchResolutionRepository resolutionRepository;

    @EventListener
    public void onTransition(ApplicationTransitioned event) {
        Application application = applicationRepository.findById(event.applicationId()).orElse(null);
        if (application == null) {
            return;
        }
        String template = templateFor(event.transition(), application.getTransferredFromId() != null);
        if (template == null) {
            return;
        }
        Map<String, String> vars = new LinkedHashMap<>();
        slotOf(application).ifPresent(slot -> {
            vars.put("slotLabel", GuidanceFormat.slotLabel(slot));
            vars.put("matchRunAt", GuidanceFormat.dateTime(slot.getMatchRunAt()));
        });
        if (template.startsWith("REFUND")) {
            vars.put("amount", paymentAmount(application));
        }
        notificationService.enqueue(application.getUserId(), template, vars, payload(application),
                template + ":app-" + application.getId());
    }

    @EventListener
    public void onMatchFailure(MatchFailureRecorded event) {
        Application application = applicationRepository.findById(event.applicationId()).orElse(null);
        if (application == null) {
            return;
        }
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("reason", application.getMatchFailReason() == null
                ? "조건에 맞는 테이블을 찾지 못했어요." : application.getMatchFailReason());
        List<Long> offered = resolutionRepository.findByApplicationId(application.getId())
                .map(r -> r.getOfferedSlotIds()).orElse(List.of());
        if (!offered.isEmpty()) {
            String labels = eventSlotRepository.findByIdIn(offered).stream()
                    .map(GuidanceFormat::slotLabel).collect(Collectors.joining(", "));
            vars.put("alternatives", "추천 일정: " + labels + ". ");
        }
        notificationService.enqueue(application.getUserId(), "MATCH_FAILED", vars, payload(application),
                "MATCH_FAILED:app-" + application.getId());
    }

    /** 알림으로 만들 전이만 템플릿 코드를 돌려준다. */
    static String templateFor(Transition transition, boolean transferred) {
        if (transferred) {
            // 일정 이동으로 생긴 새 신청은 "일정 이동 완료" 하나만 알린다 (신청·결제 알림 생략)
            switch (transition) {
                case M1 -> {
                    return "TRANSFERRED";
                }
                case A2, P1, P1a, P2 -> {
                    return null;
                }
                default -> {
                }
            }
        }
        return switch (transition) {
            case A2 -> "APPLICATION_SUBMITTED";
            case P1 -> "PAYMENT_PENDING";
            case P1a, P2 -> "PAYMENT_PAID";
            case M4, M10 -> "REALLOCATING";
            case P6, P7a -> "REFUND_REQUESTED";
            case P9, P12 -> "REFUND_COMPLETED";
            case A4 -> "APPLICATION_CANCELED";
            default -> null;
        };
    }

    private Optional<EventSlot> slotOf(Application application) {
        Long slotId = application.getAssignedSlotId();
        if (slotId == null) {
            slotId = application.getSlotChoicesByPriority().stream().findFirst()
                    .map(ApplicationSlotChoice::getEventSlotId).orElse(null);
        }
        return slotId == null ? Optional.empty() : eventSlotRepository.findById(slotId);
    }

    private String paymentAmount(Application application) {
        long amount = application.getPaymentId() == null ? 0
                : paymentRepository.findById(application.getPaymentId()).map(p -> p.getAmount()).orElse(0L);
        return NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원";
    }

    private static Map<String, Object> payload(Application application) {
        return Map.of("applicationId", application.getId(), "link", "/app/applications/" + application.getId());
    }
}
