package com.siktak.common.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;

/**
 * 스케줄러 설정. 틱은 메모리만 보고(ScheduledJobRunner), 작업이 실제로 실행될 때만
 * ShedLock(테이블: shedlock, V1)으로 잠가 인스턴스가 여러 개여도 한 번만 실행되게 한다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    /**
     * 트랜잭션 매니저를 앱의 JPA 매니저로 지정한다. 지정하지 않으면 ShedLock이 자체 DataSourceTransactionManager를 만들고,
     * 바깥 JPA 트랜잭션이 있을 때 같은 커넥션을 커밋해 버린다 (2.4에서 테스트 롤백이 깨져 발견).
     */
    @Bean
    public LockProvider lockProvider(DataSource dataSource, PlatformTransactionManager transactionManager) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .withTransactionManager(transactionManager)
                        .usingDbTime()
                        .build());
    }

    @Bean
    public LockingTaskExecutor lockingTaskExecutor(LockProvider lockProvider) {
        return new DefaultLockingTaskExecutor(lockProvider);
    }
}
