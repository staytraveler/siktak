package com.siktak.matching.domain.alternative;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 대체 일정 추천 (PRD STA-04·05, matching.md 5-8). 순수 함수.
 *
 * <p>모집 중이고 매칭 시각이 아직 오지 않았고 본인이 선택하지 않은 회차 중,
 * 본인이 선택했던 지역 → 다른 지역, 그 안에서 날짜 빠른 순 → slotId 순으로 최대 {@code limit}개.
 */
public final class AlternativeSlotRecommender {

    private AlternativeSlotRecommender() {
    }

    /**
     * @param chosenSlotIds 미배치 참가자가 선택했던 회차
     * @param slots         전체 회차 (선택했던 회차 포함 — 지역을 알아내는 데 쓴다)
     * @param now           기준 시각 (호출 측이 {@code Clock}에서 읽어 넘긴다)
     * @param limit         추천 개수 ({@code matching.alternative-count}, 기본 3)
     */
    public static List<AlternativeSlot> recommend(Collection<Long> chosenSlotIds, List<SlotOption> slots, Instant now,
            int limit) {
        Objects.requireNonNull(now, "now");
        if (limit < 0) {
            throw new IllegalArgumentException("limit은 0 이상: " + limit);
        }
        Set<Long> chosen = new HashSet<>(chosenSlotIds);
        Set<Long> chosenRegions = new HashSet<>();
        for (SlotOption s : slots) {
            if (chosen.contains(s.id())) {
                chosenRegions.add(s.regionId());
            }
        }

        return slots.stream()
                .filter(s -> s.open() && s.matchRunAt().isAfter(now) && !chosen.contains(s.id()))
                .sorted(Comparator.<SlotOption>comparingInt(s -> chosenRegions.contains(s.regionId()) ? 0 : 1)
                        .thenComparing(SlotOption::date)
                        .thenComparingLong(SlotOption::id))
                .limit(limit)
                .map(s -> new AlternativeSlot(s.id(), chosenRegions.contains(s.regionId())
                        ? AlternativeSlot.Reason.CHOSEN_REGION
                        : AlternativeSlot.Reason.OTHER_REGION))
                .toList();
    }
}
