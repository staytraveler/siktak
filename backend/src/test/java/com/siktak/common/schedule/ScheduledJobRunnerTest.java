package com.siktak.common.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

class ScheduledJobRunnerTest {

    private static final Instant NOW = Instant.parse("2026-10-18T03:00:00Z");

    private final ScheduledJobRegistry registry = new ScheduledJobRegistry();
    private final RecordingHandler handler = new RecordingHandler();
    /** shedlock 테이블 대신 호출 횟수를 센다 — 0이면 락 저장소(DB)에 접근하지 않은 것 */
    private final CountingLockProvider lockProvider = new CountingLockProvider();
    private final MutableClock clock = new MutableClock(NOW);

    private ScheduledJobRunner runner(boolean enabled) {
        return new ScheduledJobRunner(registry, List.of(handler), new DefaultLockingTaskExecutor(lockProvider),
                clock, enabled);
    }

    @Test
    void 도래한_작업이_없으면_틱은_락_저장소도_핸들러도_건드리지_않는다() {
        ScheduledJobRunner runner = runner(true);
        registry.schedule(new ScheduledJob("MATCH_RUN", 1L, NOW.plusSeconds(60)));

        runner.tick();
        runner.tick();

        assertThat(lockProvider.calls).isZero();
        assertThat(handler.attempts).isZero();
        assertThat(handler.loadCalls).isZero();
    }

    @Test
    void 도래한_작업만_락을_잡고_실행하고_목록에서_뺀다() {
        ScheduledJobRunner runner = runner(true);
        registry.schedule(new ScheduledJob("MATCH_RUN", 1L, NOW.minusSeconds(1)));
        registry.schedule(new ScheduledJob("MATCH_RUN", 2L, NOW));
        registry.schedule(new ScheduledJob("MATCH_RUN", 3L, NOW.plusSeconds(1)));

        int executed = runner.runDue(JobTrigger.tick());

        assertThat(executed).isEqualTo(2);
        assertThat(handler.executed).containsExactly(1L, 2L);
        assertThat(lockProvider.lockNames).containsExactly("MATCH_RUN:1", "MATCH_RUN:2");
        assertThat(registry.upcoming(10)).extracting(ScheduledJob::targetId).containsExactly(3L);
    }

    @Test
    void 기동_시_DB에서_한_번_적재하고_이미_지난_작업은_즉시_실행한다_catch_up() {
        ScheduledJobRunner runner = runner(true);
        handler.pending = List.of(
                new ScheduledJob("MATCH_RUN", 10L, NOW.minus(Duration.ofDays(1))),
                new ScheduledJob("MATCH_RUN", 11L, NOW.plus(Duration.ofDays(1))));

        int caughtUp = runner.reloadAndCatchUp();

        assertThat(caughtUp).isEqualTo(1);
        assertThat(handler.loadCalls).isEqualTo(1);
        assertThat(handler.executed).containsExactly(10L);
        assertThat(handler.lastTrigger).isEqualTo(JobTrigger.catchUp());
        assertThat(registry.upcoming(10)).extracting(ScheduledJob::targetId).containsExactly(11L);
    }

    @Test
    void 시계를_앞당기면_그_시각까지_도래한_작업이_실행된다() {
        ScheduledJobRunner runner = runner(true);
        registry.schedule(new ScheduledJob("MATCH_RUN", 1L, NOW.plus(Duration.ofDays(1))));
        assertThat(runner.runDue(JobTrigger.tick())).isZero();

        clock.set(NOW.plus(Duration.ofDays(1)));
        int executed = runner.runDue(JobTrigger.byOperator(99L, "데모 J1"));

        assertThat(executed).isEqualTo(1);
        assertThat(handler.lastTrigger.triggeredBy()).isEqualTo(99L);
    }

    @Test
    void 실패하면_1분_뒤로_미루고_3번_실패하면_목록에서_뺀다() {
        ScheduledJobRunner runner = runner(true);
        handler.failing = true;
        registry.schedule(new ScheduledJob("MATCH_RUN", 5L, NOW));

        runner.runDue(JobTrigger.tick());
        assertThat(registry.upcoming(1).get(0).dueAt()).isEqualTo(NOW.plus(ScheduledJobRunner.RETRY_DELAY));

        runner.runDue(JobTrigger.tick()); // 재시도 시각 전 → 실행 안 함
        assertThat(handler.attempts).isEqualTo(1);

        clock.set(NOW.plus(Duration.ofMinutes(1)));
        runner.runDue(JobTrigger.tick()); // 2회째
        clock.set(NOW.plus(Duration.ofMinutes(2)));
        runner.runDue(JobTrigger.tick()); // 3회째 → 포기

        assertThat(handler.attempts).isEqualTo(ScheduledJobRunner.MAX_ATTEMPTS);
        assertThat(registry.size()).isZero();
    }

    @Test
    void 다른_인스턴스가_락을_갖고_있으면_실행하지_않고_다음_틱에_다시_본다() {
        ScheduledJobRunner runner = runner(true);
        lockProvider.available = false;
        registry.schedule(new ScheduledJob("MATCH_RUN", 7L, NOW));

        assertThat(runner.runDue(JobTrigger.tick())).isZero();
        assertThat(handler.executed).isEmpty();
        assertThat(registry.size()).isEqualTo(1);

        lockProvider.available = true;
        assertThat(runner.runDue(JobTrigger.tick())).isEqualTo(1);
    }

    @Test
    void 비활성화되면_틱과_catch_up이_아무것도_하지_않는다() {
        ScheduledJobRunner disabled = runner(false);
        handler.pending = List.of(new ScheduledJob("MATCH_RUN", 1L, NOW.minusSeconds(1)));

        assertThat(disabled.reloadAndCatchUp()).isZero();
        disabled.tick();

        assertThat(handler.loadCalls).isZero();
        assertThat(lockProvider.calls).isZero();
    }

    @Test
    void 처리기가_없는_작업_종류는_목록에서_빼고_다른_작업은_계속_실행한다() {
        ScheduledJobRunner runner = runner(true);
        registry.schedule(new ScheduledJob("UNKNOWN", 1L, NOW));
        registry.schedule(new ScheduledJob("MATCH_RUN", 2L, NOW));

        runner.runDue(JobTrigger.tick());

        assertThat(handler.executed).containsExactly(2L);
        assertThat(registry.size()).isZero();
    }

    @Test
    void 실행_중_시각이_다시_등록되면_새_일정은_지우지_않는다() {
        ScheduledJob original = new ScheduledJob("MATCH_RUN", 1L, NOW);
        ScheduledJob updated = new ScheduledJob("MATCH_RUN", 1L, NOW.plus(Duration.ofDays(2)));
        registry.schedule(original);
        registry.schedule(updated);

        registry.complete(original);

        assertThat(registry.upcoming(1)).containsExactly(updated);
    }

    static class RecordingHandler implements ScheduledJobHandler {
        final List<Long> executed = new ArrayList<>();
        List<ScheduledJob> pending = List.of();
        JobTrigger lastTrigger;
        boolean failing;
        int attempts;
        int loadCalls;

        @Override
        public String jobType() {
            return "MATCH_RUN";
        }

        @Override
        public List<ScheduledJob> loadPending() {
            loadCalls++;
            return pending;
        }

        @Override
        public void execute(long targetId, JobTrigger trigger) {
            attempts++;
            if (failing) {
                throw new IllegalStateException("boom");
            }
            executed.add(targetId);
            lastTrigger = trigger;
        }
    }

    static class CountingLockProvider implements LockProvider {
        int calls;
        boolean available = true;
        final List<String> lockNames = new ArrayList<>();

        @Override
        public Optional<SimpleLock> lock(LockConfiguration configuration) {
            calls++;
            if (!available) {
                return Optional.empty();
            }
            lockNames.add(configuration.getName());
            return Optional.of(() -> {
            });
        }
    }

    static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant value) {
            now = value;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
