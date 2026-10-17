package com.siktak.venue.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.venue.domain.VenueSlotCapacity;

public interface VenueSlotCapacityRepository extends JpaRepository<VenueSlotCapacity, Long> {

    List<VenueSlotCapacity> findByEventSlotIdOrderByVenueIdAsc(Long eventSlotId);

    Optional<VenueSlotCapacity> findByVenueIdAndEventSlotId(Long venueId, Long eventSlotId);
}
