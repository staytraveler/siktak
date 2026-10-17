package com.siktak.content.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.siktak.profile.domain.ConversationStyle;
import com.siktak.profile.domain.Interest;

/**
 * 대화 콘텐츠 1건 ({@code conversation_contents}).
 *
 * @param interest 연관 관심사. {@code null}이면 일반({@code GENERAL}) 콘텐츠
 * @param style    어울리는 대화 스타일. 없으면 {@code null}
 */
public record ConversationContent(
        long id,
        ContentType type,
        Interest interest,
        ConversationStyle style,
        String title,
        String body) {

    /** {@code tags} 값으로 쓰는 일반 콘텐츠 태그. */
    public static final String GENERAL_TAG = "GENERAL";

    public ConversationContent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
    }

    public boolean general() {
        return interest == null;
    }

    /**
     * {@code tags} 문자열 목록(관심사 코드 또는 {@code GENERAL}, 대화 스타일 코드)으로 만든다.
     * 관심사·대화 스타일은 각각 최대 1개.
     */
    public static ConversationContent fromTags(long id, ContentType type, List<String> tags, String title, String body) {
        Interest interest = null;
        ConversationStyle style = null;
        for (String tag : tags) {
            if (GENERAL_TAG.equals(tag)) {
                continue;
            }
            if (isEnum(Interest.class, tag)) {
                if (interest != null) {
                    throw new IllegalArgumentException("관심사 태그는 1개만: " + id);
                }
                interest = Interest.valueOf(tag);
            } else if (isEnum(ConversationStyle.class, tag)) {
                if (style != null) {
                    throw new IllegalArgumentException("대화 스타일 태그는 1개만: " + id);
                }
                style = ConversationStyle.valueOf(tag);
            } else {
                throw new IllegalArgumentException("알 수 없는 태그 " + tag + ": " + id);
            }
        }
        return new ConversationContent(id, type, interest, style, title, body);
    }

    /** {@code tags} jsonb로 저장할 값. {@link #fromTags}의 역. */
    public List<String> tags() {
        List<String> tags = new ArrayList<>();
        tags.add(interest == null ? GENERAL_TAG : interest.name());
        if (style != null) {
            tags.add(style.name());
        }
        return List.copyOf(tags);
    }

    private static <E extends Enum<E>> boolean isEnum(Class<E> type, String name) {
        for (E e : type.getEnumConstants()) {
            if (e.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
