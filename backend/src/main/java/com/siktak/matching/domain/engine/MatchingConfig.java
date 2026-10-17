package com.siktak.matching.domain.engine;

import java.util.Objects;

import com.siktak.matching.domain.engine.score.ScoreWeights;

/**
 * 운영자·개발자가 바꿀 수 있는 매칭 설정값 (matching.md 9장).
 *
 * <p>변경 불가 값(테이블 4~6명, 나이 차 8)은 {@link com.siktak.matching.domain.engine.constraint.HardConstraints}에 둔다.
 *
 * @param alternativeCount 대체 일정 추천 수. 추천은 엔진 밖에서 하며 여기서는 실행 설정 스냅샷용으로만 들고 있다.
 */
public record MatchingConfig(
        ScoreWeights weights,
        double minScore,
        int maxSwapIterations,
        int maxMergeRounds,
        int alternativeCount) {

    public MatchingConfig {
        Objects.requireNonNull(weights, "weights");
        if (maxSwapIterations < 0 || maxMergeRounds < 0 || alternativeCount < 0) {
            throw new IllegalArgumentException("반복·개수 설정은 0 이상이어야 한다");
        }
    }

    public static MatchingConfig defaults() {
        return new MatchingConfig(ScoreWeights.defaults(), 55, 500, 3, 3);
    }
}
