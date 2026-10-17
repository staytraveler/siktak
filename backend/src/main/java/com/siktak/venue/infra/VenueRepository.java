package com.siktak.venue.infra;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.venue.domain.Venue;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    List<Venue> findByRegionIdOrderByIdAsc(Long regionId);
}
