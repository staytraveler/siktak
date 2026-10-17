package com.siktak.matching.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.matching.domain.MatchResolution;
import com.siktak.matching.domain.ResolutionStatus;

public interface MatchResolutionRepository extends JpaRepository<MatchResolution, Long> {

    Optional<MatchResolution> findByApplicationId(Long applicationId);

    List<MatchResolution> findByResolutionStatusOrderByIdAsc(ResolutionStatus resolutionStatus);
}
