package com.siktak.user.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.common.config.SecurityConfig;
import com.siktak.common.error.GlobalExceptionHandler;
import com.siktak.common.security.JwtConfig;
import com.siktak.common.security.JwtTokenService;
import com.siktak.common.security.JwtUserAuthenticationConverter;
import com.siktak.user.application.AuthService;
import com.siktak.user.domain.AccountStatus;
import com.siktak.user.domain.User;
import com.siktak.user.domain.UserRole;
import com.siktak.user.infra.UserRepository;

/** 3.1 로그인·JWT·권한 분리 (ACC-01, PRD 16장). */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtUserAuthenticationConverter.class, GlobalExceptionHandler.class,
        AuthService.class, AuthSecurityTest.ProbeController.class})
class AuthSecurityTest {

    private static final String PASSWORD = "siktak1234";

    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private UserRepository userRepository;
    @MockitoBean private Clock clock;

    @Test
    void 로그인하면_Bearer_토큰과_사용자_정보를_돌려주고_me로_조회된다() throws Exception {
        user(1L, "user01", UserRole.PARTICIPANT);
        String body = mockMvc.perform(login("user01", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.user.loginId").value("user01"))
                .andExpect(jsonPath("$.user.role").value("PARTICIPANT"))
                .andReturn().getResponse().getContentAsString();
        String token = body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("참가자"));
    }

    @Test
    void 비밀번호가_틀리거나_없는_아이디면_401_INVALID_CREDENTIALS() throws Exception {
        user(1L, "user01", UserRole.PARTICIPANT);
        mockMvc.perform(login("user01", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mockMvc.perform(login("nobody", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 정지_계정은_로그인_403이고_정지_전_토큰도_401로_막힌다() throws Exception {
        User user = user(1L, "user01", UserRole.PARTICIPANT);
        String auth = bearer(user);
        ReflectionTestUtils.setField(user, "accountStatus", AccountStatus.BANNED);

        mockMvc.perform(login("user01", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BANNED"));
        mockMvc.perform(get("/api/v1/probe").header("Authorization", auth))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 토큰이_없으면_401_참가자가_운영자_API를_부르면_403_운영자는_둘_다_200() throws Exception {
        String participant = bearer(user(1L, "user01", UserRole.PARTICIPANT));
        String operator = bearer(user(2L, "admin", UserRole.OPERATOR));

        mockMvc.perform(get("/api/v1/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/probe").header("Authorization", participant)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/demo").header("Authorization", participant))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/admin/demo").header("Authorization", operator)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/probe").header("Authorization", operator)).andExpect(status().isOk());
    }

    @Test
    void 만료되거나_위조된_토큰과_예전_X_User_Id_헤더는_401() throws Exception {
        User user = user(1L, "user01", UserRole.PARTICIPANT);
        Clock past = Clock.fixed(Instant.now().minusSeconds(25 * 3600), ZoneOffset.UTC);
        String expired = new JwtTokenService(jwtEncoder, past).issue(user).accessToken();
        String forged = new JwtTokenService(new JwtConfig("x".repeat(40)).jwtEncoder(), Clock.systemUTC())
                .issue(user).accessToken();

        mockMvc.perform(get("/api/v1/probe").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/probe").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/probe").header("X-User-Id", "1"))
                .andExpect(status().isUnauthorized());
    }

    private User user(Long id, String loginId, UserRole role) {
        User user = User.create(loginId, role, role == UserRole.OPERATOR ? "운영자" : "참가자", null,
                passwordEncoder.encode(PASSWORD));
        ReflectionTestUtils.setField(user, "id", id);
        given(userRepository.findById(id)).willReturn(Optional.of(user));
        given(userRepository.findByLoginId(loginId)).willReturn(Optional.of(user));
        return user;
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenService.issue(user).accessToken();
    }

    private static RequestBuilder login(String loginId, String password) {
        return post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}");
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/v1/probe")
        String probe() {
            return "ok";
        }

        @GetMapping("/api/v1/admin/demo")
        String demo() {
            return "ok";
        }
    }
}
