package com.siktak.common.infra;

import javax.sql.DataSource;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * DB 연결 확인. 실패해도 예외를 던지지 않고 false를 반환한다.
 * 헬스체크가 DB 장애로 실패하면 Render가 인스턴스를 재시작하므로, DB 상태는 응답 본문으로만 알린다.
 */
@Slf4j
@Component
public class DatabaseHealthChecker {

    private static final int QUERY_TIMEOUT_SECONDS = 2;

    private final JdbcTemplate jdbcTemplate;

    public DatabaseHealthChecker(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
    }

    public boolean isUp() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1;
        } catch (DataAccessException e) {
            log.warn("DB health check failed: {}", e.getMessage());
            return false;
        }
    }
}
