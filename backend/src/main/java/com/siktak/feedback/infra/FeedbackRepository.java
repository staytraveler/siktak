package com.siktak.feedback.infra;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.feedback.domain.Feedback;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    boolean existsByApplicationId(Long applicationId);

    List<Feedback> findByUserIdOrderByIdDesc(Long userId);
}
