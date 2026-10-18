package com.siktak.feedback.domain;

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

/** 사람별 선호 (FDB-04). 비공개 — 쓴 사람 본인과 매칭 엔진만 읽는다. 상대에게는 어떤 API로도 내보내지 않는다. */
@Getter
@Entity
@Table(name = "person_preferences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonPreference extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_user_id", nullable = false)
    private Long fromUserId;

    @Column(name = "to_user_id", nullable = false)
    private Long toUserId;

    @Column(name = "dining_table_id", nullable = false)
    private Long diningTableId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PreferenceType preference;

    public static PersonPreference of(Long fromUserId, Long toUserId, Long diningTableId, PreferenceType preference) {
        PersonPreference p = new PersonPreference();
        p.fromUserId = fromUserId;
        p.toUserId = toUserId;
        p.diningTableId = diningTableId;
        p.preference = preference;
        return p;
    }

    public void change(PreferenceType preference) {
        this.preference = preference;
    }
}
