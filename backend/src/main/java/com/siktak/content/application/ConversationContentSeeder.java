package com.siktak.content.application;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.content.domain.ConversationContent;
import com.siktak.content.infra.ConversationContentSeed;

import lombok.RequiredArgsConstructor;

/**
 * 대화 콘텐츠 시드 투입 (demo-scenario.md 7장, 4.2 JSON 28개). 테이블이 비어 있을 때만 넣는다 (여러 번 불러도 같음).
 * 콘텐츠 엔티티·게시 기능은 5.x에서 붙는다.
 */
@Component
@RequiredArgsConstructor
public class ConversationContentSeeder {

    private final NamedParameterJdbcTemplate jdbc;

    /** @return 새로 넣은 개수 (이미 있으면 0) */
    @Transactional
    public int seedIfEmpty() {
        Integer existing = jdbc.queryForObject("select count(*) from conversation_contents", Map.of(), Integer.class);
        if (existing != null && existing > 0) {
            return 0;
        }
        int inserted = 0;
        for (ConversationContent c : ConversationContentSeed.load()) {
            String tags = c.tags().stream().map(t -> "\"" + t + "\"").collect(Collectors.joining(",", "[", "]"));
            inserted += jdbc.update("""
                    insert into conversation_contents (content_type, title, body, tags, active)
                    values (:type, :title, :body, cast(:tags as jsonb), true)
                    """, Map.of("type", c.type().name(), "title", c.title(), "body", c.body(), "tags", tags));
        }
        return inserted;
    }
}
