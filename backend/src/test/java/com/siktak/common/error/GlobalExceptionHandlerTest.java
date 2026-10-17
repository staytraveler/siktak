package com.siktak.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.common.config.SecurityConfig;
import com.siktak.common.security.JwtConfig;
import com.siktak.common.security.JwtTokenService;
import com.siktak.common.security.JwtUserAuthenticationConverter;
import com.siktak.user.domain.User;
import com.siktak.user.domain.UserRole;
import com.siktak.user.infra.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import static org.mockito.BDDMockito.given;

@WebMvcTest(GlobalExceptionHandlerTest.TestController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtUserAuthenticationConverter.class, GlobalExceptionHandler.class,
        GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private Clock clock;

    @Test
    void BusinessException은_코드와_메시지와_details로_응답한다() throws Exception {
        String auth = asParticipant(1L);
        mockMvc.perform(get("/test/business").header("Authorization", auth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"))
                .andExpect(jsonPath("$.message").value("상태 충돌"))
                .andExpect(jsonPath("$.details.from").value("PENDING"));
    }

    @Test
    void 검증_실패는_INVALID_REQUEST와_필드_목록으로_응답한다() throws Exception {
        String auth = asParticipant(1L);
        mockMvc.perform(post("/test/validate").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details.fields[0].field").value("name"));
    }

    @Test
    void 읽을_수_없는_본문은_INVALID_REQUEST로_응답한다() throws Exception {
        String auth = asParticipant(1L);
        mockMvc.perform(post("/test/validate").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 예상하지_못한_예외는_내부_정보를_숨기고_INTERNAL_ERROR로_응답한다() throws Exception {
        String auth = asParticipant(1L);
        mockMvc.perform(get("/test/boom").header("Authorization", auth))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void 인증_헤더가_없으면_401_UNAUTHORIZED_형식으로_응답한다() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 참가자가_운영자_API를_호출하면_403_FORBIDDEN_형식으로_응답한다() throws Exception {
        String auth = asParticipant(1L);
        mockMvc.perform(get("/api/v1/admin/anything").header("Authorization", auth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Autowired
    private JwtTokenService jwtTokenService;

    /** 참가자 사용자를 만들고 Authorization 헤더 값(Bearer 토큰)을 돌려준다. */
    private String asParticipant(Long id) {
        User user = User.create("user" + id, UserRole.PARTICIPANT, "참가자", null);
        ReflectionTestUtils.setField(user, "id", id);
        given(userRepository.findById(id)).willReturn(Optional.of(user));
        return "Bearer " + jwtTokenService.issue(user).accessToken();
    }

    @RestController
    static class TestController {

        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "상태 충돌", Map.of("from", "PENDING"));
        }

        @PostMapping("/test/validate")
        void validate(@Valid @RequestBody NameRequest request) {
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("secret internal detail");
        }

        record NameRequest(@NotBlank String name) {
        }
    }
}
