package com.siktak.common.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.siktak.common.schedule.JobTrigger;
import com.siktak.common.schedule.ScheduledJobRegistry;
import com.siktak.common.schedule.ScheduledJobRunner;
import com.siktak.common.seed.DemoSeedService;
import com.siktak.common.seed.SeedContext;
import com.siktak.slot.application.MatchRunJobHandler;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.infra.EventSlotRepository;

import net.javacrumbs.shedlock.core.LockingTaskExecutor;

/**
 * 2.4 완료 조건: demo-v1 시드 → J1 점프 → S1·S2 매칭 자동 실행, 재시작 후 지난 MATCH_RUN catch-up.
 * 실제 PostgreSQL에서 돌고 트랜잭션 롤백으로 데이터를 남기지 않는다 (TRUNCATE도 롤백된다).
 */
@Tag("db")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DemoSeedSchedulerIntegrationTest {

    @Autowired private DemoSeedService seedService;
    @Autowired private DemoClockService clockService;
    @Autowired private DemoClock clock;
    @Autowired private MatchRunJobHandler matchRunJobHandler;
    @Autowired private LockingTaskExecutor lockingTaskExecutor;
    @Autowired private EventSlotRepository eventSlotRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    @AfterEach
    void resetClock() {
        clock.setOffset(Duration.ZERO);
    }

    /** 실제 빈과 같은 구성으로, 테스트가 직접 제어하는 실행기 (test 프로필은 자동 실행기를 끈다) */
    private ScheduledJobRunner newRunner() {
        return new ScheduledJobRunner(new ScheduledJobRegistry(), List.of(matchRunJobHandler), lockingTaskExecutor,
                clock, true);
    }

    @Test
    void 시드_투입_후_J1로_시계를_옮기면_S1_S2_매칭이_자동_실행되고_재시작하면_지난_작업을_catch_up한다() {
        SeedContext seed = seedService.reset(null);
        entityManager.flush(); // JdbcTemplate 집계 전에 JPA 변경을 반영 (실제로는 커밋 시 반영됨)

        // --- 시드 (demo-scenario 2·3·6장)
        assertThat(count("users")).isEqualTo(31); // admin + user01~29 + demo01
        assertThat(count("event_slots")).isEqualTo(5);
        assertThat(count("applications")).isEqualTo(27); // 신청 없는 user25·26·demo01 제외
        assertThat(count("applications WHERE payment_status = 'PAID' AND match_status = 'WAITING'")).isEqualTo(26);
        assertThat(jdbc.queryForObject("""
                SELECT a.payment_status FROM applications a JOIN users u ON u.id = a.user_id WHERE u.login_id = 'user16'
                """, String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE login_id = 'admin'", String.class))
                .startsWith("$2");
        assertThat(count("profiles WHERE price_range IS NOT NULL")).isEqualTo(30);
        assertThat(clockService.offset()).isZero();
        // 확장 시드: 차단(3-1)·식당 4곳(2장)·대화 콘텐츠 28개(7장)
        assertThat(count("user_blocks")).isEqualTo(1);
        assertThat(count("venues")).isEqualTo(4);
        assertThat(count("conversation_contents")).isEqualTo(28);

        // --- 기동: 5개 회차의 MATCH_RUN이 메모리에 적재되고, 아직 도래 전이라 실행 없음
        ScheduledJobRunner runner = newRunner();
        assertThat(runner.reloadAndCatchUp()).isZero();
        assertThat(count("match_runs")).isZero();

        // --- J1: S1·S2 매칭 마감 시각으로 이동 → 즉시 틱 → 두 회차 매칭 자동 실행
        EventSlot s1 = eventSlotRepository.findById(seed.slot("S1")).orElseThrow();
        clockService.jumpTo(s1.getMatchRunAt(), seed.user("admin"));
        int executed = runner.runDue(JobTrigger.byOperator(seed.user("admin"), "데모 도구 J1 점프"));
        entityManager.flush();

        assertThat(executed).isEqualTo(2);
        // v2 엔진은 도래한 S1·S2를 실행 1건으로 묶고, 두 번째 작업은 같은 실행을 돌려준다 (4.4)
        assertThat(count("match_runs")).isEqualTo(1);
        assertThat(count("dining_tables WHERE event_slot_id = " + seed.slot("S1"))).isPositive();
        assertThat(count("dining_tables WHERE event_slot_id = " + seed.slot("S2"))).isPositive();
        assertThat(count("dining_tables WHERE event_slot_id = " + seed.slot("S3"))).isZero();
        // M2 이력: 주체 SCHEDULER, 실행자 admin (states.md actor 표)
        assertThat(count("status_histories WHERE transition_code = 'M2' AND actor_type = 'SCHEDULER' AND actor_id = "
                + seed.user("admin"))).isPositive();

        // --- 서버가 꺼진 사이 S3 매칭 시각이 지났다고 가정 → 재기동 시 catch-up
        EventSlot s3 = eventSlotRepository.findById(seed.slot("S3")).orElseThrow();
        clockService.jumpTo(s3.getMatchRunAt().plusSeconds(60), seed.user("admin"));
        ScheduledJobRunner restarted = newRunner();
        int caughtUp = restarted.reloadAndCatchUp();
        entityManager.flush();

        assertThat(caughtUp).isEqualTo(1);
        assertThat(count("match_runs")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT params->>'trigger' FROM match_runs ORDER BY id DESC LIMIT 1",
                String.class)).isEqualTo("SCHEDULER");
        // 이미 매칭된 S1·S2는 다시 적재되지 않고, 다시 실행해도 run_key로 중복이 생기지 않는다
        matchRunJobHandler.execute(seed.slot("S1"), JobTrigger.tick());
        assertThat(count("match_runs")).isEqualTo(2);
    }

    private int count(String fromWhere) {
        return jdbc.queryForObject("SELECT count(*) FROM " + fromWhere, Integer.class);
    }
}
