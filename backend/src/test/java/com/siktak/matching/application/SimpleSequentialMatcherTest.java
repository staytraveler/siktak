package com.siktak.matching.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SimpleSequentialMatcherTest {

    private final SimpleSequentialMatcher matcher = new SimpleSequentialMatcher();

    @ParameterizedTest(name = "{0}명 → 테이블 크기 {1}, 미배치 {2}")
    @CsvSource({
            "0, '', 0",
            "3, '', 3",
            "4, '4', 0",
            "6, '6', 0",
            "7, '6', 1",
            "8, '4 4', 0",
            "12, '6 6', 0",
            "13, '5 4 4', 0",
            "19, '5 5 5 4', 0",
    })
    void 모든_테이블은_4명에서_6명이고_순서대로_고르게_나뉜다(int n, String expectedSizes, int expectedUnplaced) {
        TableMatcher.Result result = matcher.match(candidates(n));

        List<Integer> sizes = result.groups().stream().map(List::size).toList();
        List<Integer> expected = expectedSizes.isBlank() ? List.of()
                : java.util.Arrays.stream(expectedSizes.split(" ")).map(Integer::parseInt).toList();
        assertThat(sizes).isEqualTo(expected);
        assertThat(result.unplaced()).hasSize(expectedUnplaced);
        assertThat(sizes).allMatch(s -> s >= 4 && s <= 6);

        // 신청 순서가 유지되고 한 사람이 두 번 배치되지 않는다
        List<Long> flattened = result.groups().stream().flatMap(List::stream).toList();
        assertThat(flattened).doesNotHaveDuplicates();
        assertThat(flattened).isSortedAccordingTo(Long::compare);
    }

    private static List<TableMatcher.Candidate> candidates(int n) {
        return LongStream.rangeClosed(1, n)
                .mapToObj(i -> new TableMatcher.Candidate(i, 1000 + i, Instant.EPOCH.plusSeconds(i)))
                .toList();
    }
}
