package com.siktak.matching.domain.engine.score;

/** 선호 점수 가중치 (matching.md 3-1). 합계 100이면 총점이 0~100이 된다. */
public record ScoreWeights(double s1, double s2, double s3, double s4, double s5, double s6) {

    public ScoreWeights {
        for (double w : new double[] {s1, s2, s3, s4, s5, s6}) {
            if (w < 0) {
                throw new IllegalArgumentException("가중치는 0 이상이어야 한다");
            }
        }
    }

    public static ScoreWeights defaults() {
        return new ScoreWeights(20, 20, 18, 16, 14, 12);
    }

    public double of(ScoreCode code) {
        return switch (code) {
            case S1 -> s1;
            case S2 -> s2;
            case S3 -> s3;
            case S4 -> s4;
            case S5 -> s5;
            case S6 -> s6;
        };
    }
}
