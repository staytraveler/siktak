package com.siktak.safety.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.safety.domain.ReportStatus;
import com.siktak.safety.domain.SafetyReport;

public interface SafetyReportRepository extends JpaRepository<SafetyReport, Long> {

    Optional<SafetyReport> findByReporterIdAndReportedIdAndDiningTableId(Long reporterId, Long reportedId,
            Long diningTableId);

    List<SafetyReport> findAllByOrderByIdDesc();

    List<SafetyReport> findByReportStatusInOrderByIdDesc(List<ReportStatus> statuses);
}
