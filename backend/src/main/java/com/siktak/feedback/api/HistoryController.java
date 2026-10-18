package com.siktak.feedback.api;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.common.security.AuthUser;
import com.siktak.feedback.application.HistoryService;

import lombok.RequiredArgsConstructor;

/** 참가 이력(RET-01)과 다음 일정 추천(RET-02). */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    @GetMapping("/history")
    public List<HistoryService.HistoryItem> history(@AuthenticationPrincipal AuthUser user) {
        return historyService.history(user.id());
    }

    @GetMapping("/recommendations")
    public List<HistoryService.Recommendation> recommendations(@AuthenticationPrincipal AuthUser user) {
        return historyService.recommendations(user.id());
    }
}
