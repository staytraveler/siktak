package com.siktak.common.settings;

import java.util.HashMap;
import java.util.Map;

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

/** 정책 수치·매칭 가중치·데모 시계 등 키-값 설정 (erd.md settings). */
@Getter
@Entity
@Table(name = "settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Setting extends BaseTimeEntity {

    @Id
    @Column(name = "key")
    private String key;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> value = new HashMap<>();

    @Column(name = "updated_by")
    private Long updatedBy;

    public static Setting of(String key, Map<String, Object> value, Long updatedBy) {
        Setting setting = new Setting();
        setting.key = key;
        setting.value = new HashMap<>(value);
        setting.updatedBy = updatedBy;
        return setting;
    }

    public void change(Map<String, Object> value, Long updatedBy) {
        this.value = new HashMap<>(value);
        this.updatedBy = updatedBy;
    }
}
