package com.siktak.content.infra;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.siktak.content.domain.ContentType;
import com.siktak.content.domain.ConversationContent;
import com.siktak.profile.domain.ConversationStyle;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** DB의 대화 콘텐츠(게시 가능한 것)와 멤버 대화 스타일을 읽는다 (CON-01·02). */
@Component
@RequiredArgsConstructor
public class ConversationContentReader {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbc;

    /** active인 콘텐츠 전부. 행 id가 콘텐츠 id다. */
    public List<ConversationContent> activePool() {
        return jdbc.query("""
                select id, content_type, title, body, tags::text as tags
                from conversation_contents where active order by id
                """, Map.of(), (rs, i) -> ConversationContent.fromTags(rs.getLong("id"),
                ContentType.valueOf(rs.getString("content_type")), parseTags(rs.getString("tags")),
                rs.getString("title"), rs.getString("body")));
    }

    /** 멤버 대화 스타일. 미입력(또는 알 수 없는 값)은 {@code null}로 채워 인원 수를 유지한다. */
    public List<ConversationStyle> memberStyles(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        Map<Long, String> byUser = new java.util.HashMap<>();
        jdbc.query("select user_id, conversation_style from profiles where user_id in (:ids)",
                Map.of("ids", userIds), rs -> {
                    byUser.put(rs.getLong("user_id"), rs.getString("conversation_style"));
                });
        List<ConversationStyle> styles = new ArrayList<>();
        for (Long userId : userIds) {
            String value = byUser.get(userId);
            styles.add(value == null ? null : parseStyle(value));
        }
        return styles;
    }

    private static ConversationStyle parseStyle(String value) {
        try {
            return ConversationStyle.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<String> parseTags(String json) {
        return JSON.readValue(json, STRINGS);
    }
}
