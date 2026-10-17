package com.siktak.matching.infra;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.matching.domain.DiningTable;

public interface DiningTableRepository extends JpaRepository<DiningTable, Long> {

    List<DiningTable> findByEventSlotIdOrderByIdAsc(Long eventSlotId);

    List<DiningTable> findByMatchRunIdOrderByIdAsc(Long matchRunId);
}
