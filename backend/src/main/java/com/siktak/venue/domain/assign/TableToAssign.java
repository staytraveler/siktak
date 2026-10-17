package com.siktak.venue.domain.assign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 식당을 배정할 확정 테이블.
 *
 * @param preferredPriceRanges 멤버별 희망 가격대 ({@code profiles.price_range}). 미입력 멤버는 {@code null}
 */
public record TableToAssign(long tableId, int memberCount, List<String> preferredPriceRanges) {

    public TableToAssign {
        // List.copyOf는 null 원소를 허용하지 않아 미입력 멤버를 담을 수 없다.
        preferredPriceRanges = Collections.unmodifiableList(new ArrayList<>(preferredPriceRanges));
    }
}
