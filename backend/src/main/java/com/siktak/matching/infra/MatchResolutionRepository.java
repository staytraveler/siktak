package com.siktak.matching.infra;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.matching.domain.MatchResolution;

public interface MatchResolutionRepository extends JpaRepository<MatchResolution, Long> {

    Optional<MatchResolution> findByApplicationId(Long applicationId);
}
