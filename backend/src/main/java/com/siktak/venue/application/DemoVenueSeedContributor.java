package com.siktak.venue.application;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.siktak.common.seed.SeedContext;
import com.siktak.common.seed.SeedContributor;

import lombok.RequiredArgsConstructor;

/** demo-v1 시드에 식당 4곳·회차별 수용량을 붙인다 (docs/demo-scenario.md 2장). */
@Component
@RequiredArgsConstructor
public class DemoVenueSeedContributor implements SeedContributor {

    private final DemoVenueSeed demoVenueSeed;

    @Override
    public int order() {
        return 100;
    }

    @Override
    public void contribute(SeedContext context) {
        demoVenueSeed.seed(context.region("성수"), context.region("홍대/합정"), context.region("강남"), Map.of(
                "S1", context.slot("S1"), "S2", context.slot("S2"), "S3", context.slot("S3"),
                "S4", context.slot("S4"), "S5", context.slot("S5")));
    }
}
