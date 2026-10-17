package com.siktak.common.schedule;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 다음 실행 시각을 메모리에 들고 있는 작업 목록 (decisions.md DB 절전 원칙).
 * 틱은 이 목록만 보고 도래한 작업만 실행하므로, 할 일이 없을 때 DB에 접근하지 않는다.
 * <p>트랜잭션 안에서 등록하면 커밋 후에 반영한다 (커밋 전 데이터를 작업이 읽지 않게).
 */
@Component
public class ScheduledJobRegistry {

    private final Map<ScheduledJob.Key, ScheduledJob> jobs = new ConcurrentHashMap<>();

    /** 작업을 등록하거나 실행 시각을 바꾼다. */
    public void schedule(ScheduledJob job) {
        afterCommit(() -> jobs.put(job.key(), job));
    }

    public void cancel(String jobType, long targetId) {
        afterCommit(() -> jobs.remove(new ScheduledJob.Key(jobType, targetId)));
    }

    /** now 이전에 도래한 작업 (이른 순). */
    public List<ScheduledJob> due(Instant now) {
        return jobs.values().stream()
                .filter(job -> !job.dueAt().isAfter(now))
                .sorted(Comparator.comparing(ScheduledJob::dueAt))
                .toList();
    }

    /** 실행이 끝난 작업을 지운다. 그 사이 시각이 바뀌어 다시 등록됐으면 남겨둔다. */
    public void complete(ScheduledJob job) {
        jobs.remove(job.key(), job);
    }

    public void reschedule(ScheduledJob job, Instant newDueAt) {
        jobs.replace(job.key(), job, new ScheduledJob(job.jobType(), job.targetId(), newDueAt));
    }

    public void replaceAll(Collection<ScheduledJob> loaded) {
        jobs.clear();
        loaded.forEach(job -> jobs.put(job.key(), job));
    }

    /** 다음 예정 작업 (상태 화면용). */
    public List<ScheduledJob> upcoming(int limit) {
        return jobs.values().stream().sorted(Comparator.comparing(ScheduledJob::dueAt)).limit(limit).toList();
    }

    public int size() {
        return jobs.size();
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
