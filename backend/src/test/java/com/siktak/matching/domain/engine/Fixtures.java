package com.siktak.matching.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.siktak.matching.domain.engine.constraint.HardConstraints;
import com.siktak.profile.domain.Gender;
import com.siktak.profile.domain.Interest;
import com.siktak.profile.domain.Mbti;
import com.siktak.profile.domain.Trait;

/** 매칭 엔진 테스트 공용 입력 빌더와 불변식 검사. */
final class Fixtures {

    static final LocalDate D = LocalDate.of(2026, 10, 20);

    private Fixtures() {
    }

    static SlotInfo slot(long id, LocalDate date, long regionId) {
        return new SlotInfo(id, date, regionId);
    }

    /**
     * 기본값: 출생 1997, 남성, MBTI 미입력, 관심사 FOOD, 본인 성향 LISTENER, 희망 성향 없음, 회차 1, 결제·계정 정상.
     * 기본값끼리 4~6명이면 성비만 맞으면 최소 점수를 넉넉히 넘는다.
     */
    static Cand cand(long applicationId) {
        return new Cand(applicationId);
    }

    static final class Cand {
        private final long applicationId;
        private long userId;
        private int birthYear = 1997;
        private Gender gender = Gender.MALE;
        private Mbti mbti;
        private Set<Interest> interests = Set.of(Interest.FOOD);
        private Set<Trait> selfTraits = Set.of(Trait.LISTENER);
        private Set<Trait> preferredTraits = Set.of();
        private List<Long> slots = List.of(1L);
        private boolean active = true;
        private boolean paid = true;

        Cand(long applicationId) {
            this.applicationId = applicationId;
            this.userId = applicationId;
        }

        Cand user(long userId) {
            this.userId = userId;
            return this;
        }

        Cand born(int year) {
            this.birthYear = year;
            return this;
        }

        Cand male() {
            this.gender = Gender.MALE;
            return this;
        }

        Cand female() {
            this.gender = Gender.FEMALE;
            return this;
        }

        Cand mbti(Mbti mbti) {
            this.mbti = mbti;
            return this;
        }

        Cand interests(Interest... values) {
            this.interests = Set.of(values);
            return this;
        }

        Cand self(Trait... values) {
            this.selfTraits = Set.of(values);
            return this;
        }

        Cand prefers(Trait... values) {
            this.preferredTraits = Set.of(values);
            return this;
        }

        Cand slots(Long... ids) {
            this.slots = List.of(ids);
            return this;
        }

        Cand unpaid() {
            this.paid = false;
            return this;
        }

        Cand restricted() {
            this.active = false;
            return this;
        }

        Candidate build() {
            return new Candidate(applicationId, userId, birthYear, gender, mbti, interests, selfTraits,
                    preferredTraits, slots, active, paid);
        }
    }

    static MatchingInput input(List<SlotInfo> slots, List<Candidate> candidates) {
        return input(slots, candidates, Set.of(), Set.of());
    }

    static MatchingInput input(List<SlotInfo> slots, List<Candidate> candidates, Set<UserPair> blocked,
            Set<UserPair> encounters) {
        return new MatchingInput(slots, candidates, blocked, encounters, MatchingConfig.defaults());
    }

    static MatchingResult run(MatchingInput input) {
        MatchingResult result = new MatchingEngine().run(input);
        assertInvariants(input, result);
        return result;
    }

    /** 신청 ID 범위 [from, to]를 성별 교대로 (홀수 남, 짝수 여) 만든다. */
    static List<Candidate> alternating(long from, long to) {
        List<Candidate> list = new ArrayList<>();
        for (long id = from; id <= to; id++) {
            Cand c = cand(id);
            list.add((id % 2 == 1 ? c.male() : c.female()).build());
        }
        return list;
    }

    static ProposedTable tableOf(MatchingResult result, long applicationId) {
        return result.tables().stream().filter(t -> t.members().contains(applicationId)).findFirst().orElse(null);
    }

    static List<PlacementLog> logsOf(MatchingResult result, long applicationId) {
        return result.placementLogs().stream().filter(l -> l.applicationId() == applicationId).toList();
    }

    static PlacementLog lastLog(MatchingResult result, long applicationId) {
        List<PlacementLog> logs = logsOf(result, applicationId);
        return logs.get(logs.size() - 1);
    }

    static List<Long> ids(List<UnplacedCandidate> list) {
        return list.stream().map(UnplacedCandidate::applicationId).toList();
    }

    static List<Integer> sizesDesc(MatchingResult result) {
        return result.tables().stream().map(t -> t.members().size()).sorted((a, b) -> b - a).toList();
    }

    /** 어떤 결과든 지켜야 하는 것: 필수 조건 H3~H7, 최소 점수, 모든 후보의 결말이 정확히 하나. */
    static void assertInvariants(MatchingInput input, MatchingResult result) {
        Map<Long, Candidate> byId = new HashMap<>();
        input.candidates().forEach(c -> byId.put(c.applicationId(), c));
        Map<Long, SlotInfo> slots = new HashMap<>();
        input.slots().forEach(s -> slots.put(s.id(), s));

        Set<Long> seen = new HashSet<>();
        Set<String> userDates = new HashSet<>();
        for (ProposedTable t : result.tables()) {
            List<Candidate> members = t.members().stream().map(byId::get).toList();
            assertThat(HardConstraints.sizeOk(members.size())).as("H4 %s", t.members()).isTrue();
            assertThat(HardConstraints.allChose(t.slotId(), members)).as("H3 %s", t.members()).isTrue();
            assertThat(HardConstraints.ageGapOk(members)).as("H5 %s", t.members()).isTrue();
            assertThat(HardConstraints.conflicts(members, input.blockedPairs())).as("H6 %s", t.members()).isZero();
            assertThat(t.totalScore()).as("최소 점수 %s", t.members()).isGreaterThanOrEqualTo(input.config().minScore());
            assertThat(t.summary().conflicts()).isFalse();
            for (Candidate m : members) {
                assertThat(seen.add(m.applicationId())).as("중복 배치 %d", m.applicationId()).isTrue();
                assertThat(userDates.add(m.userId() + "@" + slots.get(t.slotId()).date()))
                        .as("H7 사용자 %d", m.userId()).isTrue();
            }
        }
        for (UnplacedCandidate u : result.deferred()) {
            assertThat(seen.add(u.applicationId())).isTrue();
        }
        for (UnplacedCandidate u : result.failed()) {
            assertThat(seen.add(u.applicationId())).isTrue();
        }
        for (ExcludedCandidate e : result.excluded()) {
            assertThat(seen.add(e.applicationId())).isTrue();
        }
        assertThat(seen).as("모든 후보의 결말").hasSize(input.candidates().size());
    }
}
