package com.siktak.matching.application;

import java.time.Instant;
import java.util.List;

/**
 * 후보를 4~6인 테이블로 묶는 매칭기 포트.
 * 2.3에서는 {@link SimpleSequentialMatcher}(가짜)를 쓰고, V2의 실제 엔진(matching.domain.engine)으로 교체한다.
 */
public interface TableMatcher {

    String algorithmVersion();

    Result match(List<Candidate> candidates);

    record Candidate(Long applicationId, Long userId, Instant submittedAt) {
    }

    /** groups: 테이블별 신청 ID 목록, unplaced: 배치되지 못한 신청 ID */
    record Result(List<List<Long>> groups, List<Long> unplaced) {
    }
}
