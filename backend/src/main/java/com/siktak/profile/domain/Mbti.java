package com.siktak.profile.domain;

/** MBTI 16종 (matching.md 4장). 미입력은 {@code null}로 표현한다. */
public enum Mbti {
    ISTJ, ISFJ, INFJ, INTJ,
    ISTP, ISFP, INFP, INTP,
    ESTP, ESFP, ENFP, ENTP,
    ESTJ, ESFJ, ENFJ, ENTJ;

    public boolean extravert() {
        return name().charAt(0) == 'E';
    }

    public boolean sensing() {
        return name().charAt(1) == 'S';
    }

    public boolean thinking() {
        return name().charAt(2) == 'T';
    }

    public boolean judging() {
        return name().charAt(3) == 'J';
    }
}
