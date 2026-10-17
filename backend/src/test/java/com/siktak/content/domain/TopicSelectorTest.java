package com.siktak.content.domain;

import static com.siktak.profile.domain.ConversationStyle.BALANCED;
import static com.siktak.profile.domain.ConversationStyle.LIVELY;
import static com.siktak.profile.domain.ConversationStyle.QUIET;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.siktak.content.domain.SelectedContent.Reason;
import com.siktak.content.infra.ConversationContentSeed;
import com.siktak.profile.domain.ConversationStyle;
import com.siktak.profile.domain.Interest;

class TopicSelectorTest {

    private static final List<ConversationContent> SEED = ConversationContentSeed.load();
    private static final List<ConversationStyle> MIXED = List.of(BALANCED, LIVELY, QUIET, BALANCED);

    private static Map<Long, ConversationContent> byId() {
        Map<Long, ConversationContent> map = new HashMap<>();
        SEED.forEach(c -> map.put(c.id(), c));
        return map;
    }

    @Test
    void 시드_콘텐츠는_관심사별_TOPIC_2개_일반_TOPIC_3개_ICEBREAKER_5개() {
        assertThat(SEED).hasSize(28);
        Map<Interest, Long> perInterest = SEED.stream()
                .filter(c -> c.type() == ContentType.TOPIC && !c.general())
                .collect(Collectors.groupingBy(ConversationContent::interest, Collectors.counting()));
        assertThat(perInterest).hasSize(Interest.values().length).allSatisfy((i, n) -> assertThat(n).isEqualTo(2));
        assertThat(SEED).filteredOn(c -> c.type() == ContentType.TOPIC && c.general()).hasSize(3);
        assertThat(SEED).filteredOn(c -> c.type() == ContentType.ICEBREAKER).hasSize(5);
        assertThat(SEED).extracting(ConversationContent::id).doesNotHaveDuplicates();
    }

    @Test
    void 공통관심사_3개_이상이면_상위3개에서_주제_하나씩과_아이스브레이킹() {
        List<Interest> common = List.of(Interest.TRAVEL, Interest.FOOD, Interest.MOVIE, Interest.MUSIC);
        List<SelectedContent> r = TopicSelector.select(7, common, MIXED, SEED);

        assertThat(r).hasSize(4);
        assertThat(r.subList(0, 3)).extracting(SelectedContent::reason).containsOnly(Reason.COMMON_INTEREST);
        assertThat(r.subList(0, 3)).extracting(SelectedContent::interest)
                .containsExactly(Interest.TRAVEL, Interest.FOOD, Interest.MOVIE);
        Map<Long, ConversationContent> byId = byId();
        for (SelectedContent s : r.subList(0, 3)) {
            assertThat(byId.get(s.contentId()).interest()).isEqualTo(s.interest());
        }
        assertThat(r.get(3).type()).isEqualTo(ContentType.ICEBREAKER);
    }

    @Test
    void 공통관심사_1개면_일반주제로_채워_최소_3개() {
        List<SelectedContent> r = TopicSelector.select(7, List.of(Interest.PET), MIXED, SEED);

        assertThat(r).extracting(SelectedContent::reason)
                .containsExactly(Reason.COMMON_INTEREST, Reason.GENERAL_FILL, Reason.GENERAL_FILL, Reason.ICEBREAKER);
        Map<Long, ConversationContent> byId = byId();
        assertThat(byId.get(r.get(1).contentId()).general()).isTrue();
        assertThat(byId.get(r.get(2).contentId()).general()).isTrue();
    }

    @Test
    void 공통관심사_0개면_일반주제_3개() {
        List<SelectedContent> r = TopicSelector.select(7, List.of(), MIXED, SEED);

        assertThat(r).extracting(SelectedContent::reason)
                .containsExactly(Reason.GENERAL_FILL, Reason.GENERAL_FILL, Reason.GENERAL_FILL, Reason.ICEBREAKER);
        assertThat(r).extracting(SelectedContent::contentId).doesNotHaveDuplicates();
    }

    @Test
    void 일반주제도_모자라면_남은_주제로_채운다() {
        List<ConversationContent> pool = SEED.stream()
                .filter(c -> !(c.general() && c.type() == ContentType.TOPIC) || c.id() == 21)
                .toList();
        List<SelectedContent> r = TopicSelector.select(7, List.of(), MIXED, pool);

        assertThat(r.stream().filter(s -> s.type() == ContentType.TOPIC)).hasSize(3);
    }

    @Test
    void 같은_입력이면_같은_결과_테이블이_다르면_다른_조합이_나올_수_있다() {
        List<Interest> common = List.of(Interest.FOOD, Interest.GAME);
        assertThat(TopicSelector.select(11, common, MIXED, SEED))
                .isEqualTo(TopicSelector.select(11, common, MIXED, SEED));

        // 콘텐츠 풀 순서와 무관
        List<ConversationContent> reversed = new ArrayList<>(SEED);
        java.util.Collections.reverse(reversed);
        assertThat(TopicSelector.select(11, common, MIXED, reversed))
                .isEqualTo(TopicSelector.select(11, common, MIXED, SEED));

        Set<List<Long>> distinct = new java.util.HashSet<>();
        for (long tableId = 1; tableId <= 20; tableId++) {
            distinct.add(TopicSelector.select(tableId, common, MIXED, SEED).stream()
                    .map(SelectedContent::contentId).toList());
        }
        assertThat(distinct).hasSizeGreaterThan(1);
    }

    @Test
    void QUIET_과반이면_QUIET_태그_콘텐츠_우선() {
        List<ConversationStyle> quiet = Arrays.asList(QUIET, QUIET, QUIET, LIVELY, null);
        Map<Long, ConversationContent> byId = byId();
        for (long tableId = 1; tableId <= 10; tableId++) {
            List<SelectedContent> r = TopicSelector.select(tableId, List.of(Interest.FOOD), quiet, SEED);
            // 관심사 주제·아이스브레이킹은 QUIET 후보가 있어 QUIET만, 일반 채움은 QUIET 1개(23)를 먼저 쓰고 나머지는 일반 주제.
            assertThat(r.get(0).contentId()).isEqualTo(2L);
            assertThat(r.get(1).contentId()).isEqualTo(23L);
            assertThat(byId.get(r.get(2).contentId()).general()).isTrue();
            assertThat(byId.get(r.get(3).contentId()).style()).isEqualTo(QUIET);
        }
    }

    @Test
    void QUIET_절반이면_우선하지_않음() {
        assertThat(TopicSelector.quietMajority(List.of(QUIET, QUIET, LIVELY, BALANCED))).isFalse();
        assertThat(TopicSelector.quietMajority(List.of(QUIET, QUIET, QUIET, BALANCED))).isTrue();
    }

    @Test
    void QUIET_태그가_없으면_무시하고_고른다() {
        List<ConversationContent> noQuiet = SEED.stream().filter(c -> c.style() == null).toList();
        List<SelectedContent> r = TopicSelector.select(3, List.of(Interest.FOOD), List.of(QUIET, QUIET, QUIET), noQuiet);

        assertThat(r).hasSize(4);
    }

    @Test
    void 태그_변환은_왕복된다() {
        ConversationContent c = byId().get(2L);
        assertThat(c.tags()).containsExactly("FOOD", "QUIET");
        assertThat(ConversationContent.fromTags(c.id(), c.type(), c.tags(), c.title(), c.body())).isEqualTo(c);
    }
}
