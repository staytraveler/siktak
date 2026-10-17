package com.siktak.payment.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.payment.domain.UserPass;
import com.siktak.payment.domain.UserPassType;
import com.siktak.payment.infra.UserPassRepository;

import lombok.RequiredArgsConstructor;

/** 보유 이용권 (states.md 1장). 크레딧 사용(P1a)은 3.x 이용권 작업에서. */
@Service
@RequiredArgsConstructor
public class UserPassService {

    private final UserPassRepository userPassRepository;
    private final Clock clock;

    /**
     * 매칭 실패 신청에 1회권 크레딧을 발급한다 (이용권 보관, states.md M8·M8a, D2).
     * 신청 하나에 크레딧 하나 — 이미 있으면 그것을 돌려준다 (멱등, V5 부분 유니크).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UserPass issueCredit(Long userId, Long sourceApplicationId) {
        return userPassRepository.findByPassTypeAndSourceApplicationId(UserPassType.SINGLE_CREDIT, sourceApplicationId)
                .orElseGet(() -> userPassRepository.save(
                        UserPass.singleCredit(userId, sourceApplicationId, Instant.now(clock))));
    }
}
