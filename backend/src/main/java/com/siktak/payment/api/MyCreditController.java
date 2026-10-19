package com.siktak.payment.api;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.common.security.AuthUser;
import com.siktak.payment.domain.PassStatus;
import com.siktak.payment.domain.UserPass;
import com.siktak.payment.domain.UserPassType;
import com.siktak.payment.infra.UserPassRepository;

import lombok.RequiredArgsConstructor;

/** 내 보관 크레딧 (매칭 실패 후 이용권 보관, PAY-03). 홈·신청 화면에서 "보관 중인 1회권"을 보여 줄 때 쓴다. */
@RestController
@RequestMapping("/api/v1/me/credits")
@RequiredArgsConstructor
public class MyCreditController {

    private final UserPassRepository userPassRepository;

    public record CreditResponse(int activeCount, List<CreditItem> items) {
    }

    public record CreditItem(Long id, Long sourceApplicationId) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public CreditResponse credits(@AuthenticationPrincipal AuthUser user) {
        List<UserPass> passes = userPassRepository.findByUserIdAndPassTypeAndPassStatus(user.id(),
                UserPassType.SINGLE_CREDIT, PassStatus.ACTIVE);
        return new CreditResponse(passes.size(),
                passes.stream().map(p -> new CreditItem(p.getId(), p.getSourceApplicationId())).toList());
    }
}
