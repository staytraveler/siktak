package com.siktak.slot.api;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateSlotRequest(
        @NotNull Long regionId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotNull @Min(4) @Max(200) Integer capacity,
        @NotNull Instant applyDeadlineAt,
        @NotNull Instant matchRunAt) {
}
