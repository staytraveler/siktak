package com.siktak.common.schedule;

import java.time.Instant;

/** 시각 기반 작업 하나. (jobType, targetId)가 같으면 같은 작업이다. */
public record ScheduledJob(String jobType, long targetId, Instant dueAt) {

    public Key key() {
        return new Key(jobType, targetId);
    }

    public record Key(String jobType, long targetId) {
    }
}
