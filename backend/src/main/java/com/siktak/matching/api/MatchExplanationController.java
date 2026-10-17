package com.siktak.matching.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.application.application.ApplicationService;
import com.siktak.common.security.AuthUser;
import com.siktak.matching.application.MatchQueryService;
import com.siktak.matching.application.MatchQueryService.Explanation;

import lombok.RequiredArgsConstructor;

/**
 * 참가자 매칭 근거 (matching.md 7-3, PRD MAT-12). 본인 신청만 볼 수 있고,
 * 다른 참가자의 개인 정보·상세 점수·차단 사실은 내보내지 않는다.
 */
@RestController
@RequestMapping("/api/v1/me/applications/{applicationId}/match-explanation")
@RequiredArgsConstructor
public class MatchExplanationController {

    private final ApplicationService applicationService;
    private final MatchQueryService queryService;

    @GetMapping
    public Explanation explain(@AuthenticationPrincipal AuthUser user, @PathVariable Long applicationId) {
        return queryService.explain(applicationService.getOwned(user.id(), applicationId));
    }
}
