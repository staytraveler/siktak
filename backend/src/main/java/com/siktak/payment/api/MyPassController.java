package com.siktak.payment.api;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.common.security.AuthUser;
import com.siktak.payment.application.UserPassService;
import com.siktak.payment.domain.PassStatus;
import com.siktak.payment.domain.PaymentPurpose;
import com.siktak.payment.domain.UserPass;
import com.siktak.payment.domain.UserPassType;
import com.siktak.payment.infra.UserPassRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/** 내 이용권(멤버십·보관 크레딧)과 멤버십 구매 (APP-04, RET-03). 결제는 모의 — 버튼 한 번으로 완료된다. */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MyPassController {

    private final UserPassService userPassService;
    private final UserPassRepository userPassRepository;
    private final Clock clock;

    public record MembershipItem(Long id, String passType, int months, long price, Instant startsAt, Instant expiresAt,
            boolean active) {
    }

    public record PassesResponse(int creditCount, MembershipItem membership, List<MembershipPlan> plans) {
    }

    public record MembershipPlan(int months, long price) {
    }

    public record PurchaseRequest(@NotNull Integer months) {
    }

    @GetMapping("/passes")
    @Transactional(readOnly = true)
    public PassesResponse passes(@AuthenticationPrincipal AuthUser user) {
        Instant now = Instant.now(clock);
        int credits = userPassRepository.findByUserIdAndPassTypeAndPassStatus(user.id(),
                UserPassType.SINGLE_CREDIT, PassStatus.ACTIVE).size();
        MembershipItem membership = userPassService.activeMembership(user.id(), now).map(p -> toItem(p, now))
                .orElse(null);
        return new PassesResponse(credits, membership, List.of(new MembershipPlan(1, PaymentPurpose.MEMBERSHIP_1M.getAmount()),
                new MembershipPlan(3, PaymentPurpose.MEMBERSHIP_3M.getAmount()),
                new MembershipPlan(6, PaymentPurpose.MEMBERSHIP_6M.getAmount())));
    }

    /** 멤버십 구매 (모의 결제). */
    @PostMapping("/memberships")
    @ResponseStatus(HttpStatus.CREATED)
    public MembershipItem buy(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody PurchaseRequest request) {
        UserPass pass = userPassService.purchaseMembership(user.id(), request.months());
        return toItem(pass, Instant.now(clock));
    }

    private static MembershipItem toItem(UserPass p, Instant now) {
        int months = switch (p.getPassType()) {
            case MEMBERSHIP_1M -> 1;
            case MEMBERSHIP_3M -> 3;
            case MEMBERSHIP_6M -> 6;
            default -> 0;
        };
        long price = switch (p.getPassType()) {
            case MEMBERSHIP_1M -> PaymentPurpose.MEMBERSHIP_1M.getAmount();
            case MEMBERSHIP_3M -> PaymentPurpose.MEMBERSHIP_3M.getAmount();
            case MEMBERSHIP_6M -> PaymentPurpose.MEMBERSHIP_6M.getAmount();
            default -> 0L;
        };
        return new MembershipItem(p.getId(), p.getPassType().name(), months, price, p.getStartsAt(), p.getExpiresAt(),
                p.isValidMembershipAt(now));
    }
}
