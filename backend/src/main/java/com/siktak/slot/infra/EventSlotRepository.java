package com.siktak.slot.infra;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.domain.SlotStatus;

public interface EventSlotRepository extends JpaRepository<EventSlot, Long> {

    @EntityGraph(attributePaths = "region")
    List<EventSlot> findAllByOrderByStartsAtAscIdAsc();

    @EntityGraph(attributePaths = "region")
    List<EventSlot> findBySlotStatusOrderByStartsAtAscIdAsc(SlotStatus status);

    @EntityGraph(attributePaths = "region")
    List<EventSlot> findByIdIn(List<Long> ids);
}
