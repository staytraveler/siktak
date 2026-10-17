package com.siktak.matching.domain.engine;

/**
 * 매칭 엔진 진입점 (MAT-02~12, matching.md).
 *
 * <p>순수 함수다. DB·시각·랜덤·외부 호출에 의존하지 않고, 같은 입력이면 항상 같은 결과를 낸다 (E23).
 * 실행 기록({@code MatchRun})과 상태 전이는 호출 측(4.4)이 이 결과를 받아 처리한다.
 */
public final class MatchingEngine {

    /** 점수 계산식이 바뀌면 올린다 (matching.md 9장). */
    public static final String ALGORITHM_VERSION = "v1";

    public MatchingResult run(MatchingInput input) {
        return new MatchingRun(input).execute();
    }
}
