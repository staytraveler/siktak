package com.siktak.matching.domain.engine.constraint;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.siktak.matching.domain.engine.Candidate;
import com.siktak.matching.domain.engine.UserPair;

/**
 * 필수 조건 (matching.md 2장). 어떤 단계에서도 완화하지 않는다.
 *
 * <p>H1·H2는 후보 추출, H7은 실행 전체 상태가 필요해 {@code MatchingRun}에서 검사한다.
 */
public final class HardConstraints {

    /** H4. PRD 필수 조건, 설정으로 바꾸지 않는다. */
    public static final int TABLE_MIN = 4;
    public static final int TABLE_MAX = 6;
    /** H5. 출생연도 차이 기준. */
    public static final int MAX_AGE_GAP = 8;

    private HardConstraints() {
    }

    /** H4. */
    public static boolean sizeOk(int size) {
        return size >= TABLE_MIN && size <= TABLE_MAX;
    }

    /** H3. 테이블 회차가 모든 멤버의 선택 회차에 포함. */
    public static boolean allChose(long slotId, Collection<Candidate> members) {
        return members.stream().allMatch(m -> m.chose(slotId));
    }

    public static int ageSpread(Collection<Candidate> members) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (Candidate m : members) {
            min = Math.min(min, m.birthYear());
            max = Math.max(max, m.birthYear());
        }
        return members.isEmpty() ? 0 : max - min;
    }

    /** H5. */
    public static boolean ageGapOk(Collection<Candidate> members) {
        return ageSpread(members) <= MAX_AGE_GAP;
    }

    /** 충돌 쌍 수: H6 위반 쌍 + 같은 사용자의 신청 쌍(H7의 테이블 내부 경우). */
    public static int conflicts(List<Candidate> members, Set<UserPair> blocked) {
        int conflicts = 0;
        for (int i = 0; i < members.size(); i++) {
            for (int j = i + 1; j < members.size(); j++) {
                if (conflicting(members.get(i), members.get(j), blocked)) {
                    conflicts++;
                }
            }
        }
        return conflicts;
    }

    public static boolean conflicting(Candidate a, Candidate b, Set<UserPair> blocked) {
        return a.userId() == b.userId() || related(a, b, blocked);
    }

    public static boolean distinctUsers(Collection<Candidate> members) {
        Set<Long> users = new HashSet<>();
        return members.stream().allMatch(m -> users.add(m.userId()));
    }

    /** 회차와 무관한 테이블 구성 조건: H5 + H6 + 사용자 중복 없음. */
    public static boolean compatible(List<Candidate> members, Set<UserPair> blocked) {
        return ageGapOk(members) && conflicts(members, blocked) == 0;
    }

    public static boolean related(Candidate a, Candidate b, Set<UserPair> pairs) {
        return !pairs.isEmpty() && a.userId() != b.userId() && pairs.contains(UserPair.of(a.userId(), b.userId()));
    }
}
