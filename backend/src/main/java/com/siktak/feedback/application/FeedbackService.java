package com.siktak.feedback.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.application.application.ApplicationService;
import com.siktak.application.domain.Application;
import com.siktak.application.domain.status.MatchStatus;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.feedback.domain.Feedback;
import com.siktak.feedback.infra.FeedbackRepository;
import com.siktak.matching.application.MatchingService;
import com.siktak.matching.domain.TableMember;

import lombok.RequiredArgsConstructor;

/**
 * 만족도 피드백 저장 (FDB-02).
 * [2.3] 참석 체크인(T1·T2)은 아직 없으므로 "확정된 테이블의 멤버"면 제출할 수 있다. 7.x에서 ATTENDED 조건으로 바꾼다.
 */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final ApplicationService applicationService;
    private final MatchingService matchingService;
    private final Clock clock;

    @Transactional
    public Feedback submit(Long userId, Long applicationId, int tableRating, int conversationRating, int venueRating,
            String comment) {
        Application application = applicationService.getOwned(userId, applicationId);
        if (application.getMatchStatus() != MatchStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "확정된 테이블에만 피드백을 남길 수 있습니다.");
        }
        if (feedbackRepository.existsByApplicationId(applicationId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 피드백을 제출했습니다.");
        }
        TableMember membership = matchingService.activeMembership(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "확정된 테이블이 없습니다."));
        return feedbackRepository.save(Feedback.submit(applicationId, membership.getDiningTableId(), userId,
                tableRating, conversationRating, venueRating, comment, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public List<Feedback> findMine(Long userId) {
        return feedbackRepository.findByUserIdOrderByIdDesc(userId);
    }
}
