package com.siktak.content.infra;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.siktak.content.domain.ContentType;
import com.siktak.content.domain.ConversationContent;

import tools.jackson.databind.json.JsonMapper;

/**
 * 대화 콘텐츠 시드 ({@code classpath:seed/conversation-contents.json}, demo-scenario.md 7장).
 *
 * <p>관심사 10종 × TOPIC 2개 + 일반 TOPIC 3개 + ICEBREAKER 5개. 시드 투입 스크립트가 이 목록을 그대로 DB에 넣는다.
 */
public final class ConversationContentSeed {

    public static final String RESOURCE = "seed/conversation-contents.json";

    private ConversationContentSeed() {
    }

    private record Row(long id, String type, List<String> tags, String title, String body) {
    }

    /** id 오름차순. */
    public static List<ConversationContent> load() {
        try (InputStream in = ConversationContentSeed.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("시드 파일 없음: " + RESOURCE);
            }
            Row[] rows = JsonMapper.builder().build().readValue(in, Row[].class);
            return Arrays.stream(rows)
                    .map(r -> ConversationContent.fromTags(r.id(), ContentType.valueOf(r.type()), r.tags(), r.title(),
                            r.body()))
                    .sorted(Comparator.comparingLong(ConversationContent::id))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
