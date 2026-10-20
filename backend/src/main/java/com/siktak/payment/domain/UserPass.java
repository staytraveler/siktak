package com.siktak.payment.domain;

import java.time.Instant;

import com.siktak.common.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 보유 이용권 (erd.md user_passes). 멤버십과 1회권 크레딧을 함께 담는다 (states.md 1장). */
@Getter
@Entity
@Table(name = "user_passes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPass extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "pass_type", nullable = false)
    private UserPassType passType;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "pass_status", nullable = false)
    private PassStatus passStatus;

    @Column(name = "source_payment_id")
    private Long sourcePaymentId;

    @Column(name = "source_application_id")
    private Long sourceApplicationId;

    @Column(name = "used_application_id")
    private Long usedApplicationId;

    /** 매칭 실패한 신청에서 생긴 1회권 크레딧. 만료 없음 (expires_at null 허용, erd.md). */
    public static UserPass singleCredit(Long userId, Long sourceApplicationId, Instant at) {
        UserPass pass = new UserPass();
        pass.userId = userId;
        pass.passType = UserPassType.SINGLE_CREDIT;
        pass.startsAt = at;
        pass.passStatus = PassStatus.ACTIVE;
        pass.sourceApplicationId = sourceApplicationId;
        return pass;
    }

    /** 기간권 멤버십 (D1: 1·3·6개월, 한 번 결제, 기간 내 횟수 제한 없음). */
    public static UserPass membership(Long userId, UserPassType type, Long sourcePaymentId, Instant startsAt,
            Instant expiresAt) {
        UserPass pass = new UserPass();
        pass.userId = userId;
        pass.passType = type;
        pass.startsAt = startsAt;
        pass.expiresAt = expiresAt;
        pass.passStatus = PassStatus.ACTIVE;
        pass.sourcePaymentId = sourcePaymentId;
        return pass;
    }

    public boolean isMembership() {
        return passType != UserPassType.SINGLE_CREDIT;
    }

    /** 멤버십이 이 시각에 유효한가. 크레딧은 항상 false. */
    public boolean isValidMembershipAt(Instant at) {
        return isMembership() && passStatus == PassStatus.ACTIVE && !startsAt.isAfter(at)
                && expiresAt != null && expiresAt.isAfter(at);
    }

    /** 신청 제출에 크레딧을 쓴다 (P1a). */
    public void use(Long applicationId) {
        this.passStatus = PassStatus.USED;
        this.usedApplicationId = applicationId;
    }
}
