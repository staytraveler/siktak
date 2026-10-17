package com.siktak.matching.domain.engine;

import java.util.List;

/**
 * 매칭 엔진 출력 (matching.md 1장).
 *
 * @param tables        확정할 테이블 (회차 ID, 생성 순)
 * @param placementLogs 참가자별 배치 기록 (발생 순)
 * @param deferred      {@code WAITING} 복귀 대상 (M4a)
 * @param failed        {@code ALTERNATIVE_OFFERED} 대상 (M6). 대체 일정 추천은 엔진 밖에서 한다.
 * @param excluded      후보 추출에서 제외된 신청
 */
public record MatchingResult(
        List<ProposedTable> tables,
        List<PlacementLog> placementLogs,
        List<UnplacedCandidate> deferred,
        List<UnplacedCandidate> failed,
        List<ExcludedCandidate> excluded,
        MatchingSummary summary) {

    public MatchingResult {
        tables = List.copyOf(tables);
        placementLogs = List.copyOf(placementLogs);
        deferred = List.copyOf(deferred);
        failed = List.copyOf(failed);
        excluded = List.copyOf(excluded);
    }
}
