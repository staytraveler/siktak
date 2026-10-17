package com.siktak.feedback.domain;

import java.time.Instant;

import com.siktak.common.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 참가 후 만족도 (FDB-02, erd.md feedbacks). 신청당 1건. 사람별 선호(person_preferences)는 7.x. */
@Getter
@Entity
@Table(name = "feedbacks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feedback extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, unique = true)
    private Long applicationId;

    @Column(name = "dining_table_id", nullable = false)
    private Long diningTableId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "table_rating", nullable = false)
    private int tableRating;

    @Column(name = "conversation_rating", nullable = false)
    private int conversationRating;

    @Column(name = "venue_rating", nullable = false)
    private int venueRating;

    @Column(name = "rejoin_intent")
    private Integer rejoinIntent;

    @Column(name = "recommend_score")
    private Integer recommendScore;

    private String comment;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    public static Feedback submit(Long applicationId, Long diningTableId, Long userId, int tableRating,
            int conversationRating, int venueRating, String comment, Instant submittedAt) {
        Feedback feedback = new Feedback();
        feedback.applicationId = applicationId;
        feedback.diningTableId = diningTableId;
        feedback.userId = userId;
        feedback.tableRating = tableRating;
        feedback.conversationRating = conversationRating;
        feedback.venueRating = venueRating;
        feedback.comment = comment;
        feedback.submittedAt = submittedAt;
        return feedback;
    }
}
