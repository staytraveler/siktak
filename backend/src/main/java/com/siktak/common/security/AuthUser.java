package com.siktak.common.security;

import com.siktak.user.domain.UserRole;

/** 인증된 요청의 사용자. 컨트롤러에서 {@code @AuthenticationPrincipal AuthUser}로 받는다. */
public record AuthUser(Long id, UserRole role) {

    public boolean isOperator() {
        return role == UserRole.OPERATOR;
    }
}
