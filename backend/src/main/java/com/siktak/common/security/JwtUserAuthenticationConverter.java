package com.siktak.common.security;

import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.siktak.user.domain.AccountStatus;
import com.siktak.user.domain.User;
import com.siktak.user.infra.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 검증된 JWT → {@link AuthUser}. 요청마다 사용자를 DB에서 읽어 역할을 정하고, 정지(BANNED)·삭제된 계정은 토큰이 유효해도 401로 막는다.
 */
@Component
@RequiredArgsConstructor
public class JwtUserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        User user = parseId(jwt.getSubject())
                .flatMap(userRepository::findById)
                .filter(u -> u.getAccountStatus() != AccountStatus.BANNED)
                .orElseThrow(() -> new BadCredentialsException("Unknown or banned user"));
        AuthUser principal = new AuthUser(user.getId(), user.getRole());
        return new UsernamePasswordAuthenticationToken(principal, jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }

    private static java.util.Optional<Long> parseId(String subject) {
        try {
            return java.util.Optional.of(Long.parseLong(subject));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }
}
