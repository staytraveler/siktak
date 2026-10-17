package com.siktak.profile.domain;

/**
 * 대화 스타일 (matching.md 4장).
 *
 * <p>MVP에서는 매칭 점수에 넣지 않고 큐레이션 문구와 대화 콘텐츠 선택에만 쓴다.
 */
public enum ConversationStyle {
    QUIET("조용한 대화"),
    BALANCED("균형"),
    LIVELY("활발한 대화");

    private final String label;

    ConversationStyle(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
