package com.siktak.common.demo;

import java.time.Instant;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.common.security.AuthUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/**
 * 테스트용 시간 이동 (헤더의 임시 시간 표시). 참가자·운영자 모두 쓸 수 있다 — 심사·시연에서 예약 흐름을 보려는 용도.
 * [추측] 서버 전체 시계가 바뀐다(모든 사용자 공통). siktak.demo.enabled=false면 enabled=false로 응답하고 변경은 404.
 */
@RestController
@RequestMapping("/api/v1/demo/clock")
@RequiredArgsConstructor
public class DemoClockController {

    private final DemoClockService clockService;
    private final DemoService demoService;

    public record ClockResponse(boolean enabled, Instant now, long offsetSeconds) {
    }

    public record SetClockRequest(@NotNull Instant at) {
    }

    @GetMapping
    public ClockResponse get() {
        return new ClockResponse(clockService.isEnabled(), clockService.now(), clockService.offset().toSeconds());
    }

    /** 지정한 시각으로 옮기고, 그 시각까지 도래한 시간 기반 작업(매칭·확정·안내 등)을 실행한다. 과거로는 안 된다. */
    @PostMapping
    public ClockResponse set(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody SetClockRequest request) {
        demoService.jumpToInstant(request.at(), user.id());
        return get();
    }
}
