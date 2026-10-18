package com.siktak.profile.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.profile.domain.Profile;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
}
