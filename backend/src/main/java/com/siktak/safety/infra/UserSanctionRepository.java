package com.siktak.safety.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.safety.domain.UserSanction;

public interface UserSanctionRepository extends JpaRepository<UserSanction, Long> {

    Optional<UserSanction> findByReportId(Long reportId);

    List<UserSanction> findByUserIdOrderByIdDesc(Long userId);
}
