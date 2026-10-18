package com.siktak.profile.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.profile.domain.ConversationStyle;
import com.siktak.profile.domain.Gender;
import com.siktak.profile.domain.Interest;
import com.siktak.profile.domain.Mbti;
import com.siktak.profile.domain.PriceRange;
import com.siktak.profile.domain.Profile;
import com.siktak.profile.domain.Trait;
import com.siktak.profile.infra.ProfileRepository;

import lombok.RequiredArgsConstructor;

/** 프로필·매칭 선호 입력 (ACC-02~05). 선택지는 matching.md 4장. */
@Service
@RequiredArgsConstructor
public class ProfileService {

    /** 만 19~45세 [결정]. 출생연도만 받으므로 연 단위(올해 - 출생연도)로 계산한다. */
    static final int MIN_AGE = 19;
    static final int MAX_AGE = 45;

    private final ProfileRepository profileRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Profile get(Long userId) {
        return profileRepository.findById(userId).orElseGet(() -> Profile.empty(userId));
    }

    @Transactional(readOnly = true)
    public boolean isComplete(Long userId) {
        return profileRepository.findById(userId).map(Profile::isComplete).orElse(false);
    }

    @Transactional
    public Profile save(Long userId, ProfileInput in) {
        List<Map<String, String>> errors = new ArrayList<>();
        int thisYear = ZonedDateTime.now(clock).getYear();

        if (in.birthYear() == null) {
            errors.add(error("birthYear", "출생연도를 입력해주세요."));
        } else {
            int age = thisYear - in.birthYear();
            if (age < MIN_AGE || age > MAX_AGE) {
                errors.add(error("birthYear", "만 19세~45세만 참여할 수 있어요."));
            }
        }
        String gender = parse(Gender.class, in.gender(), "gender", true, errors);
        String mbti = parse(Mbti.class, in.mbti(), "mbti", false, errors);
        String style = parse(ConversationStyle.class, in.conversationStyle(), "conversationStyle", false, errors);
        List<String> interests = parseList(Interest.class, in.interests(), "interests", 1, 5, errors);
        List<String> selfTraits = parseList(Trait.class, in.selfTraits(), "selfTraits", 1, 3, errors);
        List<String> preferred = parseList(Trait.class, in.preferredTraits(), "preferredTraits", 0, 3, errors);
        PriceRange price = in.priceRange() == null ? null : PriceRange.fromCode(in.priceRange());
        if (price == null) {
            errors.add(error("priceRange", "희망 식사 가격대를 선택해주세요."));
        }
        Profile existing = profileRepository.findById(userId).orElse(null);
        if (!in.privacyAgreed() && (existing == null || existing.getPrivacyAgreedAt() == null)) {
            errors.add(error("privacyAgreed", "개인정보 수집·이용에 동의해주세요."));
        }
        if (!in.policyAgreed() && (existing == null || existing.getPolicyAgreedAt() == null)) {
            errors.add(error("policyAgreed", "운영정책(환불·노쇼 포함)에 동의해주세요."));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, errors.get(0).get("reason"),
                    Map.of("fields", errors));
        }

        Profile profile = existing != null ? existing : Profile.empty(userId);
        profile.update(in.birthYear(), gender, blankToNull(in.job()), mbti, interests, selfTraits, preferred, style,
                price.code(), blankToNull(in.foodRestrictions()), blankToNull(in.allergies()), Instant.now(clock),
                in.privacyAgreed(), in.policyAgreed());
        return profileRepository.save(profile);
    }

    private static <E extends Enum<E>> String parse(Class<E> type, String value, String field, boolean required,
            List<Map<String, String>> errors) {
        if (value == null || value.isBlank()) {
            if (required) {
                errors.add(error(field, "필수 항목입니다."));
            }
            return null;
        }
        try {
            return Enum.valueOf(type, value).name();
        } catch (IllegalArgumentException e) {
            errors.add(error(field, "선택할 수 없는 값입니다."));
            return null;
        }
    }

    private static <E extends Enum<E>> List<String> parseList(Class<E> type, List<String> values, String field,
            int min, int max, List<Map<String, String>> errors) {
        List<String> distinct = values == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(values));
        if (distinct.size() < min || distinct.size() > max) {
            errors.add(error(field, min == 0 ? "최대 " + max + "개까지 고를 수 있어요."
                    : min + "~" + max + "개를 골라주세요."));
            return List.of();
        }
        for (String v : distinct) {
            try {
                Enum.valueOf(type, v);
            } catch (IllegalArgumentException | NullPointerException e) {
                errors.add(error(field, "선택할 수 없는 값입니다."));
                return List.of();
            }
        }
        return distinct;
    }

    private static Map<String, String> error(String field, String reason) {
        return Map.of("field", field, "reason", reason);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public record ProfileInput(Integer birthYear, String gender, String job, String mbti, List<String> interests,
            List<String> selfTraits, List<String> preferredTraits, String conversationStyle, String priceRange,
            String foodRestrictions, String allergies, boolean privacyAgreed, boolean policyAgreed) {
    }
}
