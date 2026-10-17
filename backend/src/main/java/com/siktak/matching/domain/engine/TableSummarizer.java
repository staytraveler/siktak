package com.siktak.matching.domain.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.siktak.matching.domain.engine.constraint.HardConstraints;
import com.siktak.matching.domain.engine.score.TableScorer;
import com.siktak.profile.domain.Gender;
import com.siktak.profile.domain.Interest;
import com.siktak.profile.domain.Mbti;

/** 테이블 설명 데이터(PRD 7.2) 생성. 순수 함수. */
final class TableSummarizer {

    /** 테이블 요약의 "공통 관심사" 기준 인원 (matching.md 3-2 S4 보조 정보). */
    static final int COMMON_INTEREST_MIN_MEMBERS = 3;

    private TableSummarizer() {
    }

    static TableSummary summarize(long slotId, List<Candidate> members, Set<UserPair> encounters,
            Set<UserPair> blocked) {
        List<TableSummary.SlotMatch> slotMatch = members.stream()
                .map(m -> new TableSummary.SlotMatch(m.applicationId(), m.choiceRank(slotId)))
                .toList();

        int minYear = members.stream().mapToInt(Candidate::birthYear).min().orElse(0);
        int maxYear = members.stream().mapToInt(Candidate::birthYear).max().orElse(0);

        int male = (int) members.stream().filter(m -> m.gender() == Gender.MALE).count();

        List<TableSummary.PreferredTraitMatch> traitMatch = members.stream()
                .map(m -> {
                    int matched = TableScorer.matchedMembers(m, members);
                    return new TableSummary.PreferredTraitMatch(
                            m.applicationId(), matched, m.preferredTraits().isEmpty() || matched > 0);
                })
                .toList();

        List<MemberPair> met = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            for (int j = i + 1; j < members.size(); j++) {
                if (HardConstraints.related(members.get(i), members.get(j), encounters)) {
                    met.add(new MemberPair(members.get(i).applicationId(), members.get(j).applicationId()));
                }
            }
        }

        return new TableSummary(
                members.size(),
                slotMatch,
                new TableSummary.AgeSummary(minYear, maxYear, maxYear - minYear),
                new TableSummary.GenderSummary(male, members.size() - male),
                mbti(members),
                commonInterests(members),
                traitMatch,
                met,
                HardConstraints.conflicts(members, blocked) > 0);
    }

    private static TableSummary.MbtiSummary mbti(List<Candidate> members) {
        int[] c = new int[8];
        int unknown = 0;
        for (Candidate m : members) {
            Mbti t = m.mbti();
            if (t == null) {
                unknown++;
                continue;
            }
            c[t.extravert() ? 0 : 1]++;
            c[t.sensing() ? 2 : 3]++;
            c[t.thinking() ? 4 : 5]++;
            c[t.judging() ? 6 : 7]++;
        }
        return new TableSummary.MbtiSummary(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], unknown);
    }

    private static List<Interest> commonInterests(List<Candidate> members) {
        Map<Interest, Integer> counts = new EnumMap<>(Interest.class);
        for (Candidate m : members) {
            for (Interest i : m.interests()) {
                counts.merge(i, 1, Integer::sum);
            }
        }
        // 정렬은 안정 정렬이라 동률이면 enum 선언 순서를 유지한다.
        return counts.entrySet().stream()
                .filter(e -> e.getValue() >= COMMON_INTEREST_MIN_MEMBERS)
                .sorted(Map.Entry.<Interest, Integer>comparingByValue(Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .toList();
    }
}
