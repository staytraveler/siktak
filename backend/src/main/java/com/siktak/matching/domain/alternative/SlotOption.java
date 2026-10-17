package com.siktak.matching.domain.alternative;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 추천 후보 회차.
 *
 * @param open       회차 상태가 {@code OPEN}(모집 중)인지. 상태 enum은 slot 도메인이 가진다
 * @param matchRunAt {@code event_slots.match_run_at}
 */
public record SlotOption(long id, LocalDate date, long regionId, boolean open, Instant matchRunAt) {

    public SlotOption {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(matchRunAt, "matchRunAt");
    }
}
