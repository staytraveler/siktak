package com.siktak.common.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 서버 시각의 단일 출처. 코드에서는 now()를 직접 호출하지 않고 이 Clock을 주입받아 쓴다 (CLAUDE.md 6장).
 *
 * <p>데모용 시간 제어(docs/demo-scenario.md의 settings.demo.clock-offset)는 이후 이 빈을
 * offset을 반영하는 Clock으로 교체해 구현한다.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
