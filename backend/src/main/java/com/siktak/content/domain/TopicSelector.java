package com.siktak.content.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import com.siktak.profile.domain.ConversationStyle;
import com.siktak.profile.domain.Interest;

/**
 * 테이블 대화 주제 선택 (PRD CON-01·02, 4.2 지시서 2장). 순수 함수.
 *
 * <ol>
 *   <li>공통 관심사 상위 3개에서 각 1개씩 TOPIC</li>
 *   <li>3개 미만이면 일반 TOPIC으로 채워 최소 3개</li>
 *   <li>ICEBREAKER 1개</li>
 *   <li>같은 조건의 후보가 여럿이면 (tableId, contentId) 해시가 가장 작은 것 — 테이블마다 다르고, 같은 입력이면 같다</li>
 *   <li>멤버 대화 스타일이 QUIET 과반이면 QUIET 태그 콘텐츠 우선 (없으면 무시)</li>
 * </ol>
 */
public final class TopicSelector {

    public static final int MIN_TOPICS = 3;
    public static final int MAX_INTEREST_TOPICS = 3;

    private TopicSelector() {
    }

    /**
     * @param commonInterests 테이블 공통 관심사, 공유 인원 많은 순 (매칭 엔진 {@code TableSummary.commonInterests})
     * @param memberStyles    멤버 대화 스타일 (미입력 멤버는 {@code null})
     * @param pool            게시 가능한(active) 콘텐츠
     * @return TOPIC들(관심사 순 → 일반 채움) 다음 ICEBREAKER 순서
     */
    public static List<SelectedContent> select(long tableId, List<Interest> commonInterests,
            List<ConversationStyle> memberStyles, List<ConversationContent> pool) {
        boolean quiet = quietMajority(memberStyles);
        Set<Long> used = new HashSet<>();
        List<SelectedContent> selected = new ArrayList<>();

        for (Interest interest : commonInterests.stream().distinct().limit(MAX_INTEREST_TOPICS).toList()) {
            ConversationContent c = pick(tableId, pool, used, quiet,
                    x -> x.type() == ContentType.TOPIC && x.interest() == interest);
            if (c != null) {
                used.add(c.id());
                selected.add(new SelectedContent(c.id(), c.type(), SelectedContent.Reason.COMMON_INTEREST, interest));
            }
        }

        // 일반 주제로 채우고, 일반 주제도 모자라면 남은 아무 주제로 채운다.
        List<Predicate<ConversationContent>> fillers = List.of(
                x -> x.type() == ContentType.TOPIC && x.general(),
                x -> x.type() == ContentType.TOPIC);
        for (Predicate<ConversationContent> filler : fillers) {
            while (selected.size() < MIN_TOPICS) {
                ConversationContent c = pick(tableId, pool, used, quiet, filler);
                if (c == null) {
                    break;
                }
                used.add(c.id());
                selected.add(new SelectedContent(c.id(), c.type(), SelectedContent.Reason.GENERAL_FILL, null));
            }
        }

        ConversationContent ice = pick(tableId, pool, used, quiet, x -> x.type() == ContentType.ICEBREAKER);
        if (ice != null) {
            selected.add(new SelectedContent(ice.id(), ice.type(), SelectedContent.Reason.ICEBREAKER, null));
        }
        return List.copyOf(selected);
    }

    static boolean quietMajority(List<ConversationStyle> memberStyles) {
        long quiet = memberStyles.stream().filter(s -> s == ConversationStyle.QUIET).count();
        return quiet * 2 > memberStyles.size();
    }

    private static ConversationContent pick(long tableId, List<ConversationContent> pool, Set<Long> used,
            boolean quiet, Predicate<ConversationContent> condition) {
        List<ConversationContent> candidates = pool.stream()
                .filter(c -> !used.contains(c.id()))
                .filter(condition)
                .toList();
        if (quiet && candidates.stream().anyMatch(c -> c.style() == ConversationStyle.QUIET)) {
            candidates = candidates.stream().filter(c -> c.style() == ConversationStyle.QUIET).toList();
        }
        return candidates.stream()
                .min(Comparator.<ConversationContent>comparingLong(c -> mix(tableId, c.id()))
                        .thenComparingLong(ConversationContent::id))
                .orElse(null);
    }

    /** (tableId, contentId) → 고정된 의사 난수 (SplitMix64 마무리 함수). 부호 비트를 지워 비교를 단순하게 한다. */
    static long mix(long tableId, long contentId) {
        long z = tableId * 0x9E3779B97F4A7C15L + contentId;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return z & Long.MAX_VALUE;
    }
}
