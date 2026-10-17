package com.siktak.common.schedule;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;

/**
 * 시간 기반 작업 실행기.
 * <ul>
 *   <li>1분 틱은 메모리({@link ScheduledJobRegistry})만 본다. 도래한 작업이 없으면 DB에 접근하지 않는다.</li>
 *   <li>도래한 작업만 ShedLock을 잡고 실행한다 (틱마다 락을 잡지 않음).</li>
 *   <li>기동 시 {@link #reloadAndCatchUp()}: 핸들러별로 DB에서 1회 적재하고 이미 지난 작업을 즉시 실행한다.</li>
 *   <li>실패하면 1분 뒤 다시 시도하고, {@value #MAX_ATTEMPTS}회 실패하면 목록에서 뺀다 (예외 큐 연결은 V2 exception 패키지 이후).</li>
 * </ul>
 */
@Slf4j
@Component
public class ScheduledJobRunner {

    static final int MAX_ATTEMPTS = 3;
    static final Duration RETRY_DELAY = Duration.ofMinutes(1);
    private static final Duration LOCK_AT_MOST = Duration.ofMinutes(10);

    private final ScheduledJobRegistry registry;
    private final Map<String, ScheduledJobHandler> handlers;
    private final LockingTaskExecutor lockingTaskExecutor;
    private final Clock clock;
    private final boolean enabled;
    private final Map<ScheduledJob.Key, Integer> attempts = new ConcurrentHashMap<>();

    public ScheduledJobRunner(ScheduledJobRegistry registry, List<ScheduledJobHandler> handlers,
            LockingTaskExecutor lockingTaskExecutor, Clock clock,
            @Value("${siktak.scheduler.enabled:true}") boolean enabled) {
        this.registry = registry;
        this.handlers = handlers.stream().collect(Collectors.toMap(ScheduledJobHandler::jobType, Function.identity()));
        this.lockingTaskExecutor = lockingTaskExecutor;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${siktak.scheduler.tick:PT1M}", initialDelayString = "${siktak.scheduler.tick:PT1M}")
    public void tick() {
        if (enabled) {
            runDue(JobTrigger.tick());
        }
    }

    /** 핸들러별로 DB에서 대기 작업을 다시 읽고, 이미 지난 작업을 실행한다. 기동 시·시드 초기화 후 호출. */
    public int reloadAndCatchUp() {
        if (!enabled) {
            return 0;
        }
        List<ScheduledJob> loaded = new ArrayList<>();
        handlers.values().forEach(handler -> loaded.addAll(handler.loadPending()));
        registry.replaceAll(loaded);
        attempts.clear();
        log.info("Scheduled jobs loaded: {}", loaded.size());
        return runDue(JobTrigger.catchUp());
    }

    /** 지금 도래한 작업을 실행한다. 실행된 작업 수를 돌려준다. 데모 시계를 앞당긴 직후에도 호출한다. */
    public int runDue(JobTrigger trigger) {
        List<ScheduledJob> due = registry.due(clock.instant());
        int executed = 0;
        for (ScheduledJob job : due) {
            if (runOne(job, trigger)) {
                executed++;
            }
        }
        return executed;
    }

    private boolean runOne(ScheduledJob job, JobTrigger trigger) {
        ScheduledJobHandler handler = handlers.get(job.jobType());
        if (handler == null) {
            log.error("No handler for job type {}", job.jobType());
            registry.complete(job);
            return false;
        }
        String lockName = job.jobType() + ":" + job.targetId();
        try {
            // 락 시각은 ShedLock 실제 시계 기준 (데모 시계 offset과 무관한 인프라 시각)
            LockConfiguration lock = new LockConfiguration(ClockProvider.now(), lockName, LOCK_AT_MOST, Duration.ZERO);
            LockingTaskExecutor.TaskResult<Boolean> result = lockingTaskExecutor.executeWithLock(() -> {
                handler.execute(job.targetId(), trigger);
                return Boolean.TRUE;
            }, lock);
            if (!result.wasExecuted()) {
                // 다른 인스턴스가 실행 중. 다음 틱에 다시 보고, 멱등이므로 중복 실행돼도 안전하다.
                return false;
            }
            registry.complete(job);
            attempts.remove(job.key());
            return true;
        } catch (Throwable e) {
            int count = attempts.merge(job.key(), 1, Integer::sum);
            if (count >= MAX_ATTEMPTS) {
                log.error("Job {} failed {} times, giving up", lockName, count, e);
                registry.complete(job);
                attempts.remove(job.key());
            } else {
                Instant retryAt = clock.instant().plus(RETRY_DELAY);
                log.warn("Job {} failed (attempt {}), retry at {}", lockName, count, retryAt, e);
                registry.reschedule(job, retryAt);
            }
            return false;
        }
    }
}
