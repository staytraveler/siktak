package com.siktak.profile.application;

import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.user.domain.User;
import com.siktak.user.infra.UserRepository;

import lombok.RequiredArgsConstructor;

/** 기본 정보 중 이름·연락처 입력·수정 (ACC-02). 출생연도·성별·직업은 ProfileService가 맡는다. */
@Service
@RequiredArgsConstructor
public class ContactService {

    private static final Pattern PHONE = Pattern.compile("^[0-9+ -]{9,15}$");

    private final UserRepository userRepository;

    public record Contact(String name, String phone) {
    }

    @Transactional(readOnly = true)
    public Contact get(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return new Contact(user.getName(), user.getPhone());
    }

    @Transactional
    public Contact save(Long userId, String name, String phone) {
        String n = name == null ? "" : name.trim();
        String p = phone == null ? "" : phone.trim();
        if (n.isEmpty() || n.length() > 30) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "이름을 1~30자로 입력해주세요.",
                    Map.of("fields", java.util.List.of(Map.of("field", "name", "reason", "이름을 입력해주세요."))));
        }
        if (!PHONE.matcher(p).matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "연락처를 숫자와 하이픈으로 입력해주세요. (예: 010-1234-5678)",
                    Map.of("fields", java.util.List.of(Map.of("field", "phone", "reason", "연락처 형식이 올바르지 않아요."))));
        }
        User user = userRepository.findById(userId).orElseThrow();
        user.changeContact(n, p);
        return new Contact(user.getName(), user.getPhone());
    }
}
