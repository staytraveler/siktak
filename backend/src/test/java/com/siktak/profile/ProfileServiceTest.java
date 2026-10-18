package com.siktak.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.siktak.common.error.BusinessException;
import com.siktak.profile.application.ProfileService;
import com.siktak.profile.application.ProfileService.ProfileInput;
import com.siktak.profile.domain.Profile;
import com.siktak.profile.infra.ProfileRepository;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private ProfileRepository repository;

    private ProfileService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-17T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new ProfileService(repository, clock);
    }

    private static ProfileInput input(Integer birthYear, List<String> interests, List<String> self,
            List<String> preferred, String mbti, String price, boolean privacy, boolean policy) {
        return new ProfileInput(birthYear, "FEMALE", null, mbti, interests, self, preferred, null, price, null, null,
                privacy, policy);
    }

    private static ProfileInput valid() {
        return input(1997, List.of("FOOD", "TRAVEL"), List.of("EMPATHETIC"), List.of(), "ENFP", "20K_40K", true, true);
    }

    private void assertRejected(ProfileInput in, String field) {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.save(1L, in)).isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getDetails().toString()).contains(field));
    }

    @Test
    void 정상_입력은_저장되고_완성_상태가_된다() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        when(repository.save(any(Profile.class))).thenAnswer(i -> i.getArgument(0));
        Profile saved = service.save(1L, valid());
        assertThat(saved.isComplete()).isTrue();
        assertThat(saved.getPrivacyAgreedAt()).isEqualTo(Instant.parse("2026-10-17T03:00:00Z"));
        assertThat(saved.getInterests()).containsExactly("FOOD", "TRAVEL");
    }

    @Test
    void 개인정보_동의가_없으면_거절한다() {
        assertRejected(input(1997, List.of("FOOD"), List.of("EMPATHETIC"), List.of(), null, "20K_40K", false, true),
                "privacyAgreed");
    }

    @Test
    void 운영정책_동의가_없으면_거절한다() {
        assertRejected(input(1997, List.of("FOOD"), List.of("EMPATHETIC"), List.of(), null, "20K_40K", true, false),
                "policyAgreed");
    }

    @Test
    void 만_19세_미만과_45세_초과는_거절한다() {
        assertRejected(input(2008, List.of("FOOD"), List.of("EMPATHETIC"), List.of(), null, "20K_40K", true, true),
                "birthYear");
        assertRejected(input(1980, List.of("FOOD"), List.of("EMPATHETIC"), List.of(), null, "20K_40K", true, true),
                "birthYear");
    }

    @Test
    void 관심사는_1개_이상_5개_이하다() {
        assertRejected(input(1997, List.of(), List.of("EMPATHETIC"), List.of(), null, "20K_40K", true, true),
                "interests");
        assertRejected(input(1997, List.of("FOOD", "TRAVEL", "MOVIE", "SPORTS", "READING", "MUSIC"),
                List.of("EMPATHETIC"), List.of(), null, "20K_40K", true, true), "interests");
    }

    @Test
    void 성향_개수와_선택지_코드를_검증한다() {
        assertRejected(input(1997, List.of("FOOD"), List.of(), List.of(), null, "20K_40K", true, true), "selfTraits");
        assertRejected(input(1997, List.of("FOOD"), List.of("EMPATHETIC"),
                List.of("EMPATHETIC", "PLANNER", "HUMOROUS", "LISTENER"), null, "20K_40K", true, true),
                "preferredTraits");
        assertRejected(input(1997, List.of("FOOD"), List.of("EMPATHETIC"), List.of(), "XXXX", "20K_40K", true, true),
                "mbti");
        assertRejected(input(1997, List.of("FOOD"), List.of("EMPATHETIC"), List.of(), null, "FREE", true, true),
                "priceRange");
    }

    @Test
    void 이미_동의한_사용자는_다시_저장해도_동의_시각이_유지된다() {
        Profile existing = Profile.empty(1L);
        Instant first = Instant.parse("2026-10-01T00:00:00Z");
        existing.update(1997, "FEMALE", null, null, List.of("FOOD"), List.of("LISTENER"), List.of(), null, "20K_40K",
                null, null, first, true, true);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Profile.class))).thenAnswer(i -> i.getArgument(0));
        Profile saved = service.save(1L, valid());
        assertThat(saved.getPrivacyAgreedAt()).isEqualTo(first);
    }
}
