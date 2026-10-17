package com.siktak.common.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.siktak.common.config.SecurityConfig;

@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, HealthControllerTest.FixedClockConfig.class})
class HealthControllerTest {

    private static final Instant FIXED = Instant.parse("2026-10-17T03:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 인증_없이_헬스체크를_호출하면_UP과_Clock_기준_시각을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.time").value("2026-10-17T03:00:00Z"));
    }

    @Test
    void 공개_경로가_아니면_인증이_필요하다() throws Exception {
        mockMvc.perform(get("/api/v1/anything"))
                .andExpect(status().isForbidden());
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(FIXED, ZoneOffset.UTC);
        }
    }
}
