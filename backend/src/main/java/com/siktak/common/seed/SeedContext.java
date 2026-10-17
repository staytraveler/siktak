package com.siktak.common.seed;

import java.time.Instant;
import java.util.Map;

/**
 * 시드 투입 결과. 확장 시드({@link SeedContributor})가 코드로 ID를 찾는 데 쓴다.
 *
 * @param t0      시드 기준 시각 (docs/demo-scenario.md 2장 T0)
 * @param slotIds 회차 코드(S1~S5) → event_slots.id
 * @param userIds 로그인 ID(admin, user01~user29, demo01) → users.id
 * @param regionIds 지역 이름(성수, 홍대/합정, 강남) → regions.id
 */
public record SeedContext(Instant t0, Map<String, Long> slotIds, Map<String, Long> userIds,
        Map<String, Long> regionIds) {

    public long slot(String code) {
        return require(slotIds, code);
    }

    public long user(String loginId) {
        return require(userIds, loginId);
    }

    public long region(String name) {
        return require(regionIds, name);
    }

    private static long require(Map<String, Long> ids, String key) {
        Long id = ids.get(key);
        if (id == null) {
            throw new IllegalArgumentException("Unknown seed key: " + key);
        }
        return id;
    }
}
