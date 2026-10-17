package com.siktak.matching.infra;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.matching.domain.MatchPlacementLog;

public interface MatchPlacementLogRepository extends JpaRepository<MatchPlacementLog, Long> {

    List<MatchPlacementLog> findByApplicationIdOrderByIdAsc(Long applicationId);

    List<MatchPlacementLog> findByMatchRunIdOrderByIdAsc(Long matchRunId);
}
