package com.siktak.user.api;

import java.time.Instant;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.common.security.AuthUser;
import com.siktak.user.application.AuthService;
import com.siktak.user.domain.User;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 로그인 → Access 토큰(24시간). 이후 요청은 {@code Authorization: Bearer <accessToken>}. */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.loginId(), request.password());
        return new LoginResponse(result.token().accessToken(), "Bearer", result.token().expiresAt(),
                MeResponse.of(result.user()));
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthUser authUser) {
        return MeResponse.of(authService.me(authUser.id()));
    }

    public record LoginRequest(@NotBlank String loginId, @NotBlank String password) {
    }

    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, MeResponse user) {
    }

    public record MeResponse(Long id, String loginId, String name, String role) {
        static MeResponse of(User user) {
            return new MeResponse(user.getId(), user.getLoginId(), user.getName(), user.getRole().name());
        }
    }
}
