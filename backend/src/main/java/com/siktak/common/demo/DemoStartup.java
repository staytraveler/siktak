package com.siktak.common.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.siktak.common.schedule.ScheduledJobRunner;
import com.siktak.common.seed.DemoSeedService;
import com.siktak.user.infra.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 기동 순서: 데모 시계 offset 적재 → (빈 DB면) demo-v1 자동 투입 → 작업 실행기 적재·지난 작업 catch-up.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoStartup {

    private final DemoClockService clockService;
    private final DemoSeedService seedService;
    private final ScheduledJobRunner jobRunner;
    private final UserRepository userRepository;

    @Value("${siktak.demo.auto-seed:true}")
    private boolean autoSeed;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        clockService.load();
        if (clockService.isEnabled() && autoSeed && userRepository.count() == 0) {
            log.info("Empty database: loading demo-v1 seed");
            seedService.reset(null);
        }
        int caughtUp = jobRunner.reloadAndCatchUp();
        if (caughtUp > 0) {
            log.info("Caught up {} past-due job(s) on startup", caughtUp);
        }
    }
}
