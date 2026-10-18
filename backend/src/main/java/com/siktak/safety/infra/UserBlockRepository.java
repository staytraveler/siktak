package com.siktak.safety.infra;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.safety.domain.UserBlock;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    Optional<UserBlock> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);
}
