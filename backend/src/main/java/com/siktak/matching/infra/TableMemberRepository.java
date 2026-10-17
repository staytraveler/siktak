package com.siktak.matching.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.matching.domain.TableMember;

public interface TableMemberRepository extends JpaRepository<TableMember, Long> {

    List<TableMember> findByDiningTableIdAndActiveTrue(Long diningTableId);

    Optional<TableMember> findByApplicationIdAndActiveTrue(Long applicationId);
}
