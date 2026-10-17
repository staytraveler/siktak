package com.siktak.application.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.application.domain.Application;
import com.siktak.application.domain.status.StatusHistory;
import com.siktak.application.domain.status.Transition;
import com.siktak.application.domain.status.TransitionContext;
import com.siktak.application.infra.ApplicationRepository;
import com.siktak.application.infra.StatusHistoryRepository;

import lombok.RequiredArgsConstructor;

/**
 * 신청의 신청·결제·매칭 상태를 바꾸는 유일한 경로 (CLAUDE.md 6장, states.md 0장).
 * <ul>
 *   <li>허용 전이표({@link Transition}) 밖의 전이는 INVALID_STATE_TRANSITION 예외</li>
 *   <li>전이 주체가 표의 주체와 다르면 예외</li>
 *   <li>이미 목표 상태면 상태를 바꾸지 않고(no-op) 이력만 남긴다</li>
 *   <li>모든 전이를 status_histories에 기록한다</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class StatusTransitionService {

    static final String NO_OP_PREFIX = "[no-op] ";

    private final ApplicationRepository applicationRepository;
    private final StatusHistoryRepository statusHistoryRepository;

    /** 다음 상태가 하나뿐인 전이를 적용한다. 상태가 실제로 바뀌면 true. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean apply(Application application, Transition transition, TransitionContext context) {
        return apply(application, transition, transition.singleTarget(), context);
    }

    /** 목표 상태를 지정해 전이를 적용한다 (M12처럼 다음 상태가 여러 개인 전이). 상태가 실제로 바뀌면 true. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean apply(Application application, Transition transition, Enum<?> target, TransitionContext context) {
        if (context.actorType() != transition.getActor()) {
            throw new IllegalArgumentException(
                    transition + " must be triggered by " + transition.getActor() + ", not " + context.actorType());
        }
        Enum<?> from = application.currentStatus(transition.getAxis());
        boolean changed = application.applyTransition(transition, target);
        if (application.getId() == null) {
            applicationRepository.save(application);
        }
        String reason = changed ? context.reason() : NO_OP_PREFIX + nullToEmpty(context.reason());
        statusHistoryRepository.save(StatusHistory.record(
                StatusHistory.TARGET_APPLICATION, application.getId(), transition, from, target, context, reason));
        return changed;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
