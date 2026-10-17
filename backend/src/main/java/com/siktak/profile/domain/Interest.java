package com.siktak.profile.domain;

/** 관심사 (matching.md 4장). 최대 5개. */
public enum Interest {
    FOOD("맛집"),
    TRAVEL("여행"),
    MOVIE("영화"),
    SPORTS("운동"),
    READING("독서"),
    EXHIBITION("전시"),
    MUSIC("음악"),
    GAME("게임"),
    PET("반려동물"),
    CAREER("자기계발");

    private final String label;

    Interest(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
