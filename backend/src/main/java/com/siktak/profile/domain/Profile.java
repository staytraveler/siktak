package com.siktak.profile.domain;

import java.time.Instant;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.siktak.common.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프로필 (erd.md 3-1). V2 매칭 엔진이 SQL로 직접 읽으므로 컬럼명·JSON 형식(enum 이름 배열)을 바꾸지 않는다.
 */
@Getter
@Entity
@Table(name = "profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile extends BaseTimeEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "birth_year")
    private Integer birthYear;

    private String gender;

    private String job;

    private String mbti;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> interests = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "self_traits", nullable = false)
    private List<String> selfTraits = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_traits", nullable = false)
    private List<String> preferredTraits = List.of();

    @Column(name = "conversation_style")
    private String conversationStyle;

    @Column(name = "price_range")
    private String priceRange;

    @Column(name = "food_restrictions")
    private String foodRestrictions;

    private String allergies;

    @Column(name = "privacy_agreed_at")
    private Instant privacyAgreedAt;

    @Column(name = "policy_agreed_at")
    private Instant policyAgreedAt;

    public static Profile empty(Long userId) {
        Profile p = new Profile();
        p.userId = userId;
        return p;
    }

    public void update(Integer birthYear, String gender, String job, String mbti, List<String> interests,
            List<String> selfTraits, List<String> preferredTraits, String conversationStyle, String priceRange,
            String foodRestrictions, String allergies, Instant now, boolean privacyAgreed, boolean policyAgreed) {
        this.birthYear = birthYear;
        this.gender = gender;
        this.job = job;
        this.mbti = mbti;
        this.interests = interests;
        this.selfTraits = selfTraits;
        this.preferredTraits = preferredTraits;
        this.conversationStyle = conversationStyle;
        this.priceRange = priceRange;
        this.foodRestrictions = foodRestrictions;
        this.allergies = allergies;
        // 이미 동의한 시각은 유지한다 (재저장해도 동의 시점이 바뀌지 않음)
        if (privacyAgreed && privacyAgreedAt == null) {
            privacyAgreedAt = now;
        }
        if (policyAgreed && policyAgreedAt == null) {
            policyAgreedAt = now;
        }
    }

    /** 신청에 필요한 필수 항목이 모두 채워졌는지 (ACC-02~05). 입력 검증과 같은 기준이다. */
    public boolean isComplete() {
        return birthYear != null && gender != null && !interests.isEmpty() && !selfTraits.isEmpty()
                && priceRange != null && privacyAgreedAt != null && policyAgreedAt != null;
    }
}
