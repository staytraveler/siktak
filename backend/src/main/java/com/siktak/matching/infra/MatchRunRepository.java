package com.siktak.matching.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.matching.domain.MatchRun;

public interface MatchRunRepository extends JpaRepository<MatchRun, Long> {

    Optional<MatchRun> findByRunKey(String runKey);
}
