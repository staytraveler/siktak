package com.siktak.common.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.siktak.user.domain.AccountStatus;
import com.siktak.user.infra.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * [임시 인증 — 2.3 워킹 스켈레톤] 요청 헤더 {@code X-User-Id}로 사용자를 식별한다.
 * 비밀번호 검증이 없으므로 운영용이 아니다. 3.1에서 JWT(Authorization 헤더)로 교체한다 (decisions.md [CC]).
 */
@RequiredArgsConstructor
public class HeaderUserAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-User-Id";

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && !header.isBlank()) {
            parseId(header)
                    .flatMap(userRepository::findById)
                    .filter(user -> user.getAccountStatus() != AccountStatus.BANNED)
                    .ifPresent(user -> {
                        AuthUser principal = new AuthUser(user.getId(), user.getRole());
                        var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }
        chain.doFilter(request, response);
    }

    private static java.util.Optional<Long> parseId(String value) {
        try {
            return java.util.Optional.of(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }
}
