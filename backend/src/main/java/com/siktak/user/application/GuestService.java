package com.siktak.user.application;

import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.common.demo.DemoClockService;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.common.security.JwtTokenService;
import com.siktak.profile.application.ProfileService;
import com.siktak.profile.application.ProfileService.ProfileInput;
import com.siktak.profile.domain.Interest;
import com.siktak.profile.domain.Mbti;
import com.siktak.profile.domain.Trait;
import com.siktak.user.application.AuthService.LoginResult;
import com.siktak.user.domain.User;
import com.siktak.user.domain.UserRole;
import com.siktak.user.infra.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 시연용 "새 참가자로 시작": 누를 때마다 서로 다른 참가자 계정을 하나 만들고 바로 로그인시킨다 (MVP는 외부 로그인 연동 없음).
 * 프로필은 미리 채워 두어(출생연도·성별·MBTI·관심사가 계정마다 다르다) 바로 신청할 수 있다. 신청 전 상태로 시작한다.
 * siktak.demo.enabled=false면 만들 수 없다. 시드 초기화를 하면 함께 지워진다.
 */
@Service
@RequiredArgsConstructor
public class GuestService {

    public static final String LOGIN_PREFIX = "guest-";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final ProfileService profileService;
    private final DemoClockService demoClockService;

    @Transactional
    public LoginResult start() {
        if (!demoClockService.isEnabled()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        long n = userRepository.countByLoginIdStartingWith(LOGIN_PREFIX) + 1;
        String loginId = LOGIN_PREFIX + n + "-" + UUID.randomUUID().toString().substring(0, 4);
        User user = userRepository.save(User.create(loginId, UserRole.PARTICIPANT, "심사위원 " + n,
                String.format("010-9000-%04d", n % 10000), passwordEncoder.encode(UUID.randomUUID().toString())));
        Interest[] interests = Interest.values();
        Trait[] traits = Trait.values();
        Mbti[] mbtis = Mbti.values();
        int i = (int) n;
        profileService.save(user.getId(), new ProfileInput(
                1992 + (i * 3) % 9, i % 2 == 0 ? "FEMALE" : "MALE", null, mbtis[i % mbtis.length].name(),
                List.of(interests[i % interests.length].name(), interests[(i + 3) % interests.length].name(),
                        interests[(i + 6) % interests.length].name()),
                List.of(traits[i % traits.length].name()), List.of(traits[(i + 2) % traits.length].name()), null,
                "20K_40K", null, null, true, true));
        return new LoginResult(jwtTokenService.issue(user), user);
    }
}
