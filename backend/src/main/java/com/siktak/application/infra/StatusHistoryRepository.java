package com.siktak.application.infra;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.application.domain.status.StatusHistory;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    List<StatusHistory> findByTargetTypeAndTargetIdOrderByIdAsc(String targetType, Long targetId);
}
