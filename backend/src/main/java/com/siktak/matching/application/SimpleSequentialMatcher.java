package com.siktak.matching.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * [가짜 매칭기 — 2.3 워킹 스켈레톤] 조건을 보지 않고 신청 순서대로 4~6명씩 묶는다.
 * 테이블 수 t = ceil(n/6)에서 시작해 모든 테이블이 4명 이상이 될 때까지 줄이고, 인원을 고르게 나눈다.
 * 6t명을 넘는 나머지는 미배치로 남긴다. 4.x에서 V2 엔진으로 교체한다.
 */
@Component
public class SimpleSequentialMatcher implements TableMatcher {

    static final int MIN_SIZE = 4;
    static final int MAX_SIZE = 6;

    @Override
    public String algorithmVersion() {
        return "simple-sequential-0";
    }

    @Override
    public Result match(List<Candidate> candidates) {
        int n = candidates.size();
        int tables = (n + MAX_SIZE - 1) / MAX_SIZE;
        while (tables > 0 && n < MIN_SIZE * tables) {
            tables--;
        }
        if (tables == 0) {
            return new Result(List.of(), candidates.stream().map(Candidate::applicationId).toList());
        }
        int seated = Math.min(n, MAX_SIZE * tables);
        List<List<Long>> groups = new ArrayList<>();
        int index = 0;
        for (int t = 0; t < tables; t++) {
            int size = seated / tables + (t < seated % tables ? 1 : 0);
            groups.add(candidates.subList(index, index + size).stream().map(Candidate::applicationId).toList());
            index += size;
        }
        List<Long> unplaced = candidates.subList(seated, n).stream().map(Candidate::applicationId).toList();
        return new Result(groups, unplaced);
    }
}
