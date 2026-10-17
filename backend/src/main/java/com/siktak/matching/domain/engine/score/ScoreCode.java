package com.siktak.matching.domain.engine.score;

/** 선호 점수 항목 (matching.md 3장). 선언 순서 = PRD 우선순위. */
public enum ScoreCode {
    S1("연령 밀집도"),
    S2("성비"),
    S3("MBTI 구성"),
    S4("공통 관심사"),
    S5("희망 상대 성향 충족"),
    S6("새로운 사람 비율");

    private final String label;

    ScoreCode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
