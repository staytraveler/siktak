package com.siktak.common.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.siktak.common.demo.DemoClock;

/**
 * 서버 시각의 단일 출처. 코드에서는 now()를 직접 호출하지 않고 이 Clock을 주입받아 쓴다 (CLAUDE.md 6장).
 * 데모 시계(실제 시각 + offset)이며, offset은 DemoClockService가 관리한다. 데모가 꺼져 있으면 offset은 항상 0.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public DemoClock clock() {
        return new DemoClock(Clock.system(ZONE));
    }
}
