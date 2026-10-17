package com.siktak.common.api;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.siktak.common.config.SecurityConfig;
import com.siktak.common.infra.DatabaseHealthChecker;
import com.siktak.common.security.JwtConfig;
import com.siktak.common.security.JwtUserAuthenticationConverter;
import com.siktak.user.infra.UserRepository;

@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtUserAuthenticationConverter.class, HealthControllerTest.FixedClockConfig.class})
class HealthControllerTest {

    private static final Instant FIXED = Instant.parse("2026-10-17T03:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DatabaseHealthChecker databaseHealthChecker;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void liveness는_인증_없이_UP과_Clock_기준_시각을_반환하고_DB를_조회하지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.time").value("2026-10-17T03:00:00Z"))
                .andExpect(jsonPath("$.db").doesNotExist());

        verify(databaseHealthChecker, never()).isUp();
    }

    @Test
    void DB_헬스체크는_DB_상태를_반환한다() throws Exception {
        given(databaseHealthChecker.isUp()).willReturn(true);

        mockMvc.perform(get("/api/v1/health/db"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.db").value("UP"))
                .andExpect(jsonPath("$.time").value("2026-10-17T03:00:00Z"));
    }

    @Test
    void DB가_내려가도_200으로_응답하고_db는_DOWN이다() throws Exception {
        given(databaseHealthChecker.isUp()).willReturn(false);

        mockMvc.perform(get("/api/v1/health/db"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.db").value("DOWN"));
    }

    @Test
    void 공개_경로가_아니면_인증이_필요하다() throws Exception {
        mockMvc.perform(get("/api/v1/anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(FIXED, ZoneOffset.UTC);
        }
    }
}
