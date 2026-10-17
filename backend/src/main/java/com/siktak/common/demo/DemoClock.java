package com.siktak.common.demo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 데모용으로 앞당길 수 있는 시계: 실제 시각 + offset.
 * offset은 메모리에만 들고 있으므로 {@link #instant()}는 DB를 조회하지 않는다 (decisions.md DB 절전 원칙).
 * 저장·적재는 {@link DemoClockService}가 한다. 서버 코드는 이 Clock을 통해서만 시각을 읽는다 (CLAUDE.md 6장).
 */
public class DemoClock extends Clock {

    private final Clock base;
    private final AtomicReference<Duration> offset;

    public DemoClock(Clock base) {
        this(base, new AtomicReference<>(Duration.ZERO));
    }

    private DemoClock(Clock base, AtomicReference<Duration> offset) {
        this.base = base;
        this.offset = offset;
    }

    @Override
    public ZoneId getZone() {
        return base.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new DemoClock(base.withZone(zone), offset);
    }

    @Override
    public Instant instant() {
        return base.instant().plus(offset.get());
    }

    /** 앞당기기 전 실제 시각. offset 계산에만 쓴다. */
    public Instant realInstant() {
        return base.instant();
    }

    public Duration getOffset() {
        return offset.get();
    }

    void setOffset(Duration value) {
        offset.set(value);
    }
}
