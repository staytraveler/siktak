package com.siktak.feedback.infra;

import java.time.Instant;
import java.sql.Timestamp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** 만남 이력(user_encounters, MAT-06) 기록. (낮은 ID, 높은 ID, 테이블)이 같으면 한 번만 들어간다 (멱등). */
@Component
@RequiredArgsConstructor
public class UserEncounterWriter {

    private final JdbcTemplate jdbc;

    /** @return 새로 만들어졌으면 true */
    public boolean record(Long userA, Long userB, Long diningTableId, Instant metAt) {
        long low = Math.min(userA, userB);
        long high = Math.max(userA, userB);
        return jdbc.update("""
                insert into user_encounters (user_low_id, user_high_id, dining_table_id, met_at)
                values (?, ?, ?, ?)
                on conflict (user_low_id, user_high_id, dining_table_id) do nothing
                """, low, high, diningTableId, Timestamp.from(metAt)) > 0;
    }
}
