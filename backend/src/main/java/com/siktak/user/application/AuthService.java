package com.siktak.user.application;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.common.security.JwtTokenService;
import com.siktak.user.domain.AccountStatus;
import com.siktak.user.domain.User;
import com.siktak.user.infra.UserRepository;

import lombok.RequiredArgsConstructor;

/** 아이디·비밀번호 로그인 (ACC-01 중 테스트 계정 로그인. 카카오 로그인은 P1). */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    @Transactional(readOnly = true)
    public LoginResult login(String loginId, String password) {
        User user = userRepository.findByLoginId(loginId.trim())
                .filter(u -> u.getPasswordHash() != null && passwordEncoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (user.getAccountStatus() == AccountStatus.BANNED) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
        }
        return new LoginResult(jwtTokenService.issue(user), user);
    }

    @Transactional(readOnly = true)
    public User me(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    public record LoginResult(JwtTokenService.IssuedToken token, User user) {
    }
}
