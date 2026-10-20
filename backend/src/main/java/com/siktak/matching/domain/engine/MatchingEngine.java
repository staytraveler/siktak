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

    /** 한 테이블 최대 인원 (필수 조건, PRD MAT-02). */
    public static final int TABLE_MAX_SIZE = com.siktak.matching.domain.engine.constraint.HardConstraints.TABLE_MAX;

    public MatchingResult run(MatchingInput input) {
        return new MatchingRun(input).execute();
    }

    /** 이 구성으로 테이블 점수·요약을 다시 계산한다 (운영자 긴급 수정, MAT-14). 필수 조건 검사는 호출 측이 {@link #compatible}로 한다. */
    public CancelReallocationResult.TableUpdate describe(SlotInfo slot, long tableId, java.util.List<Candidate> members,
            java.util.Set<UserPair> encounters, java.util.Set<UserPair> blocked, java.util.Set<UserPair> wantAgain,
            MatchingConfig config) {
        java.util.List<Candidate> sorted = members.stream()
                .sorted(java.util.Comparator.comparingLong(Candidate::applicationId)).toList();
        return new CancelReallocationResult.TableUpdate(tableId, sorted.stream().map(Candidate::applicationId).toList(),
                com.siktak.matching.domain.engine.score.TableScorer.score(sorted, encounters, wantAgain,
                        config.weights()),
                TableSummarizer.summarize(slot.id(), sorted, encounters, blocked));
    }

    /** 필수 조건(연령 차 8살 이내·차단·피하고 싶음 없음)을 만족하는 구성인가. */
    public boolean compatible(java.util.List<Candidate> members, java.util.Set<UserPair> blocked) {
        return com.siktak.matching.domain.engine.constraint.HardConstraints.compatible(members, blocked);
    }

    /** 확정 후 취소로 빈 자리에 넣을 대기자 한 명 고르기 (STA-07). 조건을 통과한 대기자가 없으면 비어 있다. */
    public java.util.OptionalLong pickWaiter(java.util.List<Candidate> members, java.util.List<Candidate> waiters,
            java.util.Set<UserPair> blocked, java.util.Set<UserPair> encounters, java.util.Set<UserPair> wantAgain,
            MatchingConfig config) {
        return WaitlistFill.pick(members, waiters, blocked, encounters, wantAgain, config);
    }

    /** 확정 후 취소로 인원이 줄어든 테이블 처리 (matching.md 6장, E21·E22). */
    public CancelReallocationResult reallocateAfterCancel(CancelReallocationInput input) {
        return CancelReallocation.run(input);
    }
}
