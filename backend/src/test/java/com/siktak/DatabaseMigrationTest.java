package com.siktak;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.siktak.common.infra.DatabaseHealthChecker;

/**
 * 실제 PostgreSQL에 컨텍스트를 띄워 Flyway 마이그레이션이 적용되는지 확인한다.
 * TEST_DB_URL이 없으면 건너뛴다 (로컬에 DB 정보가 없을 때).
 */
@Tag("db")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest
@ActiveProfiles("test")
class DatabaseMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseHealthChecker databaseHealthChecker;

    @Test
    void 컨텍스트가_뜨고_V1_마이그레이션으로_shedlock_테이블이_생성된다() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success",
                Integer.class);
        Integer shedlock = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'shedlock'",
                Integer.class);

        assertThat(applied).isEqualTo(1);
        assertThat(shedlock).isEqualTo(1);
        assertThat(databaseHealthChecker.isUp()).isTrue();
    }
}
