package com.siktak.matching.domain.engine;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.siktak.profile.domain.Gender;
import com.siktak.profile.domain.Interest;
import com.siktak.profile.domain.Mbti;
import com.siktak.profile.domain.Trait;

/**
 * 매칭 후보 신청 1건과 그 신청자의 매칭 프로필.
 *
 * @param mbti          미입력이면 {@code null}
 * @param slotChoices   선택 회차 ID, {@code application_slot_choices.priority} 순 (MD7)
 * @param accountActive 계정 {@code ACTIVE}이고 유효한 참가 제한이 없음 (H2)
 * @param paid          신청 {@code SUBMITTED} + 결제 {@code PAID} (H1)
 */
public record Candidate(
        long applicationId,
        long userId,
        int birthYear,
        Gender gender,
        Mbti mbti,
        Set<Interest> interests,
        Set<Trait> selfTraits,
        Set<Trait> preferredTraits,
        List<Long> slotChoices,
        boolean accountActive,
        boolean paid) {

    public Candidate {
        Objects.requireNonNull(gender, "gender");
        interests = enumSet(Interest.class, interests);
        selfTraits = enumSet(Trait.class, selfTraits);
        preferredTraits = enumSet(Trait.class, preferredTraits);
        slotChoices = List.copyOf(slotChoices);
    }

    /** 선택 순위 (1부터). 선택하지 않은 회차면 0. */
    public int choiceRank(long slotId) {
        return slotChoices.indexOf(slotId) + 1;
    }

    public boolean chose(long slotId) {
        return slotChoices.contains(slotId);
    }

    // 순회 순서가 JVM 실행마다 달라지지 않도록 EnumSet(선언 순서)으로 고정한다.
    private static <E extends Enum<E>> Set<E> enumSet(Class<E> type, Collection<E> values) {
        EnumSet<E> set = EnumSet.noneOf(type);
        if (values != null) {
            set.addAll(values);
        }
        return Collections.unmodifiableSet(set);
    }
}
