package com.siktak.feedback.infra;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.feedback.domain.PersonPreference;

public interface PersonPreferenceRepository extends JpaRepository<PersonPreference, Long> {

    List<PersonPreference> findByFromUserIdAndDiningTableIdOrderByIdAsc(Long fromUserId, Long diningTableId);
}
