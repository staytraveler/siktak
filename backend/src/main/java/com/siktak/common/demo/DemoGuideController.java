package com.siktak.common.demo;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

/**
 * 시연 가이드 화면(/guide)이 쓰는 진행 API. 참가자·운영자 모두 쓸 수 있다 — 심사위원이 한 계정으로 12단계를 따라가도록.
 * 운영자 전용 데모 도구(/api/v1/admin/demo)와 같은 동작이며, siktak.demo.enabled=false면 404다.
 * [추측] 서버 전체 상태가 바뀌므로(시간 점프·일괄 체크인 등) 모든 사용자에게 적용된다.
 */
@RestController
@RequestMapping("/api/v1/demo")
@RequiredArgsConstructor
public class DemoGuideController {

    private final DemoService demoService;
    private final DemoScenarioActions actions;
    private final DemoProgressService progressService;

    /** 현재 시각과 J1~J6 점프 대상(도달 여부). 가이드가 단계별 진행 표시에 쓴다. */
    @GetMapping("/guide")
    public DemoService.DemoStatus guide() {
        return demoService.status();
    }

    /** 시연 가이드의 단계 완료 판정과 "시스템이 자동으로 한 일" 숫자. DB 상태에서 계산한다. */
    @GetMapping("/progress")
    public DemoProgressService.Progress progress() {
        return progressService.progress();
    }

    @PostMapping("/jump/{code}")
    public DemoService.DemoStatus jump(@AuthenticationPrincipal AuthUser user, @PathVariable String code) {
        DemoService.Jump jump;
        try {
            jump = DemoService.Jump.valueOf(code.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "알 수 없는 점프 코드입니다: " + code);
        }
        return demoService.jump(jump, user.id());
    }

    @PostMapping("/check-in-all")
    public DemoScenarioActions.Result checkInAll() {
        return actions.checkInAll();
    }

    @PostMapping("/feedback-all")
    public DemoScenarioActions.Result feedbackAll() {
        return actions.feedbackAll();
    }

    @PostMapping("/round2-applications")
    public DemoScenarioActions.Result round2Applications() {
        return actions.round2Applications();
    }
}
