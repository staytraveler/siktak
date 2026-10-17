package com.siktak.matching.domain.engine;

import java.time.LocalDate;
import java.util.Objects;

/** 매칭 대상 회차. 회차는 날짜·지역으로 구분한다 (H3). */
public record SlotInfo(long id, LocalDate date, long regionId) {

    public SlotInfo {
        Objects.requireNonNull(date, "date");
    }
}
