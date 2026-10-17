package com.siktak.slot.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.slot.domain.Region;

public interface RegionRepository extends JpaRepository<Region, Long> {

    Optional<Region> findByName(String name);

    List<Region> findByActiveTrueOrderByIdAsc();
}
