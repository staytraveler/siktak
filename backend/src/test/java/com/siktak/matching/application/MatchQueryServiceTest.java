package com.siktak.matching.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MatchQueryServiceTest {

    @Test
    void 연령대_문구는_초반_중반_후반으로_나눈다() {
        assertThat(MatchQueryService.ageBand(27, 31)).isEqualTo("20대 후반~30대 초반");
        assertThat(MatchQueryService.ageBand(24, 26)).isEqualTo("20대 중반");
        assertThat(MatchQueryService.ageBand(30, 33)).isEqualTo("30대 초반");
    }
}
