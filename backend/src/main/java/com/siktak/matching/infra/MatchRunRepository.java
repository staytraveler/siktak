package com.siktak.matching.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.siktak.matching.domain.MatchRun;

public interface MatchRunRepository extends JpaRepository<MatchRun, Long> {

    Optional<MatchRun> findByRunKey(String runKey);

    List<MatchRun> findAllByOrderByIdDesc();

    /** 이 회차를 대상에 포함한 가장 최근 실행 (params.slotIds, 엔진 v2 실행). */
    @Query(value = """
            select * from match_runs
            where params -> 'slotIds' @> to_jsonb(cast(:slotId as bigint))
            order by id desc limit 1
            """, nativeQuery = true)
    Optional<MatchRun> findLatestCoveringSlot(@Param("slotId") Long slotId);
}
