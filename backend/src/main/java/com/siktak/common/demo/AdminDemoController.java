package com.siktak.common.demo;

import java.time.Duration;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

/**
 * 데모 도구 API (운영자 전용 — /api/v1/admin/** 는 OPERATOR만). siktak.demo.enabled=false면 404.
 */
@RestController
@RequestMapping("/api/v1/admin/demo")
@RequiredArgsConstructor
public class AdminDemoController {

    private final DemoService demoService;
    private final DemoScenarioActions actions;

    @GetMapping
    public DemoService.DemoStatus status() {
        return demoService.status();
    }

    /** 데이터 전체 삭제 후 demo-v1 시드 재투입, 데모 시계 초기화. */
    @PostMapping("/reset")
    public DemoService.DemoStatus reset(@AuthenticationPrincipal AuthUser operator) {
        return demoService.reset(operator.id());
    }

    @PostMapping("/jump/{code}")
    public DemoService.DemoStatus jump(@AuthenticationPrincipal AuthUser operator, @PathVariable String code) {
        DemoService.Jump jump;
        try {
            jump = DemoService.Jump.valueOf(code.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "알 수 없는 점프 코드입니다: " + code);
        }
        return demoService.jump(jump, operator.id());
    }

    /** 체크인 일괄 (시연용): 1회차 확정자 중 user13을 제외한 전원. */
    @PostMapping("/check-in-all")
    public DemoScenarioActions.Result checkInAll() {
        return actions.checkInAll();
    }

    /** 피드백 일괄 (시연용): 체크인한 참가자 중 피드백이 없는 사람에게 기본 피드백. */
    @PostMapping("/feedback-all")
    public DemoScenarioActions.Result feedbackAll() {
        return actions.feedbackAll();
    }

    /** 2회차 신청 투입: 1회차 S1 참석자 전원 + user25·user26이 S4에 신청·결제. */
    @PostMapping("/round2-applications")
    public DemoScenarioActions.Result round2Applications() {
        return actions.round2Applications();
    }

    /** 시계를 minutes만큼 앞당긴다 (+1시간 = 60, +1일 = 1440). */
    @PostMapping("/advance")
    public DemoService.DemoStatus advance(@AuthenticationPrincipal AuthUser operator,
            @RequestParam long minutes) {
        if (minutes <= 0 || minutes > 60L * 24 * 30) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "1분~30일 사이로 앞당길 수 있습니다.");
        }
        return demoService.advance(Duration.ofMinutes(minutes), operator.id());
    }
}
