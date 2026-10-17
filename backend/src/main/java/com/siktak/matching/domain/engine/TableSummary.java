package com.siktak.matching.domain.engine;

import java.util.List;

import com.siktak.profile.domain.Interest;

/**
 * 테이블 설명 데이터 (PRD 7.2, matching.md 7-1). 운영자 화면용이며 참가자에게 그대로 노출하지 않는다.
 *
 * @param slotMatch           참가자별 희망 회차 충족 (몇 순위 선택 회차인지)
 * @param commonInterests     3명 이상이 공유한 관심사 (공유 인원 내림차순)
 * @param preferredTraitMatch 참가자별 희망 성향 충족
 * @param previousEncounters  이전에 만난 적 있는 쌍
 * @param conflicts           차단·비선호 충돌 여부 (확정 테이블은 항상 false)
 */
public record TableSummary(
        int memberCount,
        List<SlotMatch> slotMatch,
        AgeSummary age,
        GenderSummary gender,
        MbtiSummary mbti,
        List<Interest> commonInterests,
        List<PreferredTraitMatch> preferredTraitMatch,
        List<MemberPair> previousEncounters,
        boolean conflicts) {

    public TableSummary {
        slotMatch = List.copyOf(slotMatch);
        commonInterests = List.copyOf(commonInterests);
        preferredTraitMatch = List.copyOf(preferredTraitMatch);
        previousEncounters = List.copyOf(previousEncounters);
    }

    public record SlotMatch(long applicationId, int choiceRank) {
    }

    public record AgeSummary(int minBirthYear, int maxBirthYear, int gap) {
    }

    public record GenderSummary(int male, int female) {
    }

    public record MbtiSummary(int e, int i, int s, int n, int t, int f, int j, int p, int unknown) {
    }

    /**
     * @param matchedMembers 본인 성향이 내 희망 성향과 하나라도 겹치는 다른 멤버 수
     * @param satisfied      희망 성향이 없거나 {@code matchedMembers >= 1}
     */
    public record PreferredTraitMatch(long applicationId, int matchedMembers, boolean satisfied) {
    }
}
