package com.siktak.exception.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.exception.domain.CaseStatus;
import com.siktak.exception.domain.ExceptionCase;

public interface ExceptionCaseRepository extends JpaRepository<ExceptionCase, Long> {

    Optional<ExceptionCase> findByCaseKey(String caseKey);

    List<ExceptionCase> findByCaseStatusOrderByIdAsc(CaseStatus status);

    List<ExceptionCase> findAllByOrderByIdAsc();
}
