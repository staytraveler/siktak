package com.siktak.profile.domain;

/** 본인 성향·희망 상대 성향 (matching.md 4장). 각각 최대 3개. */
public enum Trait {
    EMPATHETIC("공감형"),
    PLANNER("계획형"),
    ADVENTUROUS("새로운 경험형"),
    HUMOROUS("유머형"),
    LISTENER("경청형"),
    CURIOUS("호기심형");

    private final String label;

    Trait(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
