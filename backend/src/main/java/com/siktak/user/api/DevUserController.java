package com.siktak.user.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.user.infra.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * [임시 — 2.3 워킹 스켈레톤] 화면의 사용자 선택 드롭다운용 테스트 계정 목록.
 * 이름·역할만 내려준다 (연락처·프로필 제외). 3.1 로그인 도입 시 제거한다.
 */
@RestController
@RequestMapping("/api/v1/dev/users")
@RequiredArgsConstructor
public class DevUserController {

    private final UserRepository userRepository;

    @GetMapping
    public List<DevUserResponse> users() {
        return userRepository.findAllByOrderByIdAsc().stream()
                .map(u -> new DevUserResponse(u.getId(), u.getLoginId(), u.getName(), u.getRole().name()))
                .toList();
    }

    public record DevUserResponse(Long id, String loginId, String name, String role) {
    }
}
