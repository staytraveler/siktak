package com.siktak.matching.domain.engine.score;

import java.util.List;

/**
 * 테이블 점수 상세 ({@code dining_tables.score_detail}).
 *
 * @param items S1~S6 순서
 * @param total 가중 점수 합 (0~100)
 */
public record ScoreDetail(List<Item> items, double total) {

    public ScoreDetail {
        items = List.copyOf(items);
    }

    public Item item(ScoreCode code) {
        return items.get(code.ordinal());
    }

    /**
     * @param raw      원점수 0~1
     * @param weighted {@code raw × weight}
     */
    public record Item(ScoreCode code, double raw, double weight, double weighted) {
    }
}
