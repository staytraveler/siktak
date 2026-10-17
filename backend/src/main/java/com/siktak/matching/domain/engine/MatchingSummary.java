package com.siktak.matching.domain.engine;

/**
 * 실행 결과 건수 (matching.md 1장 출력 {@code summary}).
 *
 * @param candidateCount   입력 신청 수 (제외 포함)
 * @param tablesCreated    5-3에서 만든 임시 테이블 수
 * @param splitGroups      8명 이상이라 여러 테이블로 나눈 연령 그룹 수 (MAT-09 분리)
 * @param mergedTables     5-6 병합으로 새로 만든 테이블 수
 * @param reallocatedCount 재배치·병합으로 들어간 참가자 수 (확정 여부와 무관)
 * @param dissolvedTables  최소 점수 미달·최종 검증 실패로 해체한 테이블 수
 */
public record MatchingSummary(
        int candidateCount,
        int excludedCount,
        int tablesCreated,
        int splitGroups,
        int mergedTables,
        int reallocatedCount,
        int dissolvedTables,
        int confirmedTables,
        int confirmedMembers,
        int deferredCount,
        int failedCount) {
}
