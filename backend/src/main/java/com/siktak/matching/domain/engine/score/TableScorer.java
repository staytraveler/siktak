package com.siktak.matching.domain.engine.score;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.siktak.matching.domain.engine.Candidate;
import com.siktak.matching.domain.engine.UserPair;
import com.siktak.matching.domain.engine.constraint.HardConstraints;
import com.siktak.profile.domain.Gender;
import com.siktak.profile.domain.Mbti;

/**
 * 테이블 단위 선호 점수 계산 (matching.md 3-2). 순수 함수.
 *
 * <p>필수 조건 검사는 하지 않는다. 호출 측이 필수 조건을 통과한 조합에만 쓴다.
 * 각 항목은 정수 개수를 센 뒤 마지막에 한 번만 나눠서, 멤버 순서와 무관하게 같은 값이 나온다.
 * 로컬 서치에서 수없이 호출되므로 스트림·컬렉션 할당 없이 계산한다.
 */
public final class TableScorer {

    private TableScorer() {
    }

    public static ScoreDetail score(List<Candidate> members, Set<UserPair> encounters, ScoreWeights weights) {
        double[] raws = raws(members, encounters);
        List<ScoreDetail.Item> items = new ArrayList<>(raws.length);
        double total = 0;
        for (ScoreCode code : ScoreCode.values()) {
            double raw = raws[code.ordinal()];
            double weight = weights.of(code);
            double weighted = raw * weight;
            items.add(new ScoreDetail.Item(code, raw, weight, weighted));
            total += weighted;
        }
        return new ScoreDetail(items, total);
    }

    /** {@link #score}의 총점만. 같은 순서로 더하므로 {@code score(...).total()}과 정확히 같다. */
    public static double total(List<Candidate> members, Set<UserPair> encounters, ScoreWeights weights) {
        double[] raws = raws(members, encounters);
        double total = 0;
        for (ScoreCode code : ScoreCode.values()) {
            total += raws[code.ordinal()] * weights.of(code);
        }
        return total;
    }

    private static double[] raws(List<Candidate> members, Set<UserPair> encounters) {
        if (members.size() < 2) {
            throw new IllegalArgumentException("점수는 2명 이상 테이블에만 계산한다: " + members.size());
        }
        return new double[] {
                ageDensity(members),
                genderBalance(members),
                mbtiBalance(members),
                commonInterests(members),
                preferredTraits(members),
                newPeople(members, encounters)
        };
    }

    /** S1 = 1 - spread / 16. 0살 차 1.0, 8살 차 0.5. */
    static double ageDensity(List<Candidate> members) {
        return 1 - (double) HardConstraints.ageSpread(members) / (2 * HardConstraints.MAX_AGE_GAP);
    }

    /** S2 = 1 - (|남 - 여| - n % 2) / n. */
    static double genderBalance(List<Candidate> members) {
        int n = members.size();
        int male = 0;
        for (Candidate m : members) {
            if (m.gender() == Gender.MALE) {
                male++;
            }
        }
        int diff = Math.abs(male - (n - male));
        return 1 - (double) (diff - n % 2) / n;
    }

    /**
     * S3 = 0.4 × E/I + 0.2 × S/N + 0.2 × T/F + 0.2 × J/P, 축별 balance = 1 - |A - B| / k.
     *
     * <p>k는 테이블 인원이 아니라 MBTI 입력자 수다 (matching.md 11장 I4).
     */
    static double mbtiBalance(List<Candidate> members) {
        int k = 0;
        int e = 0;
        int s = 0;
        int t = 0;
        int j = 0;
        for (Candidate m : members) {
            Mbti mbti = m.mbti();
            if (mbti == null) {
                continue;
            }
            k++;
            e += mbti.extravert() ? 1 : 0;
            s += mbti.sensing() ? 1 : 0;
            t += mbti.thinking() ? 1 : 0;
            j += mbti.judging() ? 1 : 0;
        }
        if (k < 2) {
            return 0.5;
        }
        return 0.4 * axisBalance(e, k) + 0.2 * axisBalance(s, k) + 0.2 * axisBalance(t, k) + 0.2 * axisBalance(j, k);
    }

    private static double axisBalance(int a, int k) {
        return 1 - (double) Math.abs(a - (k - a)) / k;
    }

    /** S4 = 공통 관심사가 1개 이상인 쌍 / 전체 쌍. */
    static double commonInterests(List<Candidate> members) {
        int pairs = 0;
        int sharing = 0;
        for (int i = 0; i < members.size(); i++) {
            for (int j = i + 1; j < members.size(); j++) {
                pairs++;
                if (!Collections.disjoint(members.get(i).interests(), members.get(j).interests())) {
                    sharing++;
                }
            }
        }
        return (double) sharing / pairs;
    }

    /**
     * S5 = 평균(satisfied_i), satisfied_i = 다른 멤버 중 내 희망 성향을 가진 사람 수 / (n - 1). 희망 성향이 없으면 1.
     *
     * <p>모든 satisfied_i의 분모가 n - 1로 같으므로 분자 합을 먼저 구해 한 번에 나눈다.
     */
    static double preferredTraits(List<Candidate> members) {
        int n = members.size();
        int numerator = 0;
        for (Candidate me : members) {
            numerator += me.preferredTraits().isEmpty() ? n - 1 : matchedMembers(me, members);
        }
        return (double) numerator / ((long) n * (n - 1));
    }

    /** 본인 성향이 {@code me}의 희망 성향과 하나라도 겹치는 다른 멤버 수. */
    public static int matchedMembers(Candidate me, List<Candidate> members) {
        int matched = 0;
        for (Candidate other : members) {
            if (other.applicationId() != me.applicationId()
                    && !Collections.disjoint(me.preferredTraits(), other.selfTraits())) {
                matched++;
            }
        }
        return matched;
    }

    /** S6 = 1 - 이전에 만난 쌍 / 전체 쌍. */
    static double newPeople(List<Candidate> members, Set<UserPair> encounters) {
        int pairs = 0;
        int met = 0;
        for (int i = 0; i < members.size(); i++) {
            for (int j = i + 1; j < members.size(); j++) {
                pairs++;
                if (HardConstraints.related(members.get(i), members.get(j), encounters)) {
                    met++;
                }
            }
        }
        return 1 - (double) met / pairs;
    }
}
