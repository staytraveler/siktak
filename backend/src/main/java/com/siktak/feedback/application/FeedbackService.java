package com.siktak.feedback.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.application.application.ApplicationService;
import com.siktak.application.domain.status.AttendanceStatus;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.feedback.domain.Attendance;
import com.siktak.feedback.domain.Feedback;
import com.siktak.feedback.domain.FeedbackSubmittedEvent;
import com.siktak.feedback.infra.AttendanceRepository;
import com.siktak.feedback.infra.FeedbackRepository;

import lombok.RequiredArgsConstructor;

/** 만족도·재참여·추천 의향 저장 (FDB-02·03). 체크인한(ATTENDED) 참가자만 제출할 수 있다. */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final AttendanceRepository attendanceRepository;
    private final ApplicationService applicationService;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    /** 1~5점: 테이블 분위기·대화·식당·재참여 의향, 추천 의향은 0~10. 범위 검증은 API 계층이 한다. */
    @Transactional
    public Feedback submit(Long userId, Long applicationId, int tableRating, int conversationRating, int venueRating,
            int rejoinIntent, int recommendScore, String comment) {
        applicationService.getOwned(userId, applicationId);
        Attendance attendance = attendanceRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "참석 기록이 없습니다."));
        if (attendance.getAttendanceStatus() != AttendanceStatus.ATTENDED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "체크인한 참가자만 피드백을 남길 수 있습니다.");
        }
        if (feedbackRepository.existsByApplicationId(applicationId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 피드백을 제출했습니다.");
        }
        Feedback saved = feedbackRepository.save(Feedback.submit(applicationId, attendance.getDiningTableId(), userId,
                tableRating, conversationRating, venueRating, rejoinIntent, recommendScore, comment,
                Instant.now(clock)));
        events.publishEvent(new FeedbackSubmittedEvent(userId, applicationId));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Feedback> findMine(Long userId) {
        return feedbackRepository.findByUserIdOrderByIdDesc(userId);
    }

    /** 체크인했지만 아직 피드백이 없는 참석 (데모 일괄 입력용). */
    @Transactional(readOnly = true)
    public boolean hasFeedback(Long applicationId) {
        return feedbackRepository.existsByApplicationId(applicationId);
    }
}
