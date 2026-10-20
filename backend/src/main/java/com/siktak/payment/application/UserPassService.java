package com.siktak.payment.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.common.config.ClockConfig;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.payment.domain.PassStatus;
import com.siktak.payment.domain.Payment;
import com.siktak.payment.domain.PaymentPurpose;
import com.siktak.payment.domain.UserPass;
import com.siktak.payment.domain.UserPassType;
import com.siktak.payment.infra.UserPassRepository;

import lombok.RequiredArgsConstructor;

/** 보유 이용권 (states.md 1장). 크레딧 사용(P1a)은 3.x 이용권 작업에서. */
@Service
@RequiredArgsConstructor
public class UserPassService {

    private final UserPassRepository userPassRepository;
    private final PaymentService paymentService;
    private final Clock clock;

    /**
     * 멤버십 구매 (APP-04, D1): 1·3·6개월 기간권을 모의 결제로 한 번에 구매한다. 정기결제는 하지 않는다.
     * 이미 유효한 멤버십이 있으면 겹쳐 사지 않는다.
     */
    @Transactional
    public UserPass purchaseMembership(Long userId, int months) {
        PaymentPurpose purpose;
        UserPassType type;
        switch (months) {
            case 1 -> {
                purpose = PaymentPurpose.MEMBERSHIP_1M;
                type = UserPassType.MEMBERSHIP_1M;
            }
            case 3 -> {
                purpose = PaymentPurpose.MEMBERSHIP_3M;
                type = UserPassType.MEMBERSHIP_3M;
            }
            case 6 -> {
                purpose = PaymentPurpose.MEMBERSHIP_6M;
                type = UserPassType.MEMBERSHIP_6M;
            }
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST, "멤버십은 1·3·6개월 중에서 고를 수 있어요.");
        }
        Instant now = Instant.now(clock);
        if (activeMembership(userId, now).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 이용 중인 멤버십이 있어요.");
        }
        Payment payment = paymentService.prepare(userId, purpose, "membership-" + userId + "-" + now.toEpochMilli());
        if (!paymentService.approve(payment.getId())) {
            throw new BusinessException(ErrorCode.PAYMENT_FAILED);
        }
        Instant expiresAt = now.atZone(ClockConfig.ZONE).plusMonths(months).toInstant();
        return userPassRepository.save(UserPass.membership(userId, type, payment.getId(), now, expiresAt));
    }

    /** 이 시각에 유효한 멤버십 (없으면 비어 있다). */
    @Transactional(readOnly = true)
    public Optional<UserPass> activeMembership(Long userId, Instant at) {
        return userPassRepository.findByUserIdAndPassStatusOrderByIdDesc(userId, PassStatus.ACTIVE).stream()
                .filter(p -> p.isValidMembershipAt(at)).findFirst();
    }

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

    /** 가장 오래된 활성 크레딧 하나를 소진한다 (P1a). 없으면 비어 있다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<UserPass> consumeCredit(Long userId, Long applicationId) {
        return userPassRepository
                .findFirstByUserIdAndPassTypeAndPassStatusOrderByIdAsc(userId, UserPassType.SINGLE_CREDIT,
                        PassStatus.ACTIVE)
                .map(pass -> {
                    pass.use(applicationId);
                    return pass;
                });
    }
}
