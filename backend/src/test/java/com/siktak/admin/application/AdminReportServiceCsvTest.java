package com.siktak.admin.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.siktak.admin.application.AdminReportService.CsvWriter;

class AdminReportServiceCsvTest {

    @Test
    void BOM으로_시작하고_쉼표_따옴표_줄바꿈을_감싼다() {
        CsvWriter csv = new CsvWriter("이름", "메모");
        csv.row("김,철수", "그는 \"좋은\" 사람\n맞다");
        String out = csv.build();
        assertThat(out).startsWith("﻿이름,메모\r\n");
        assertThat(out).contains("\"김,철수\",\"그는 \"\"좋은\"\" 사람\n맞다\"\r\n");
    }

    @Test
    void 수식으로_시작하는_문자열은_막고_숫자_null은_그대로() {
        assertThat(CsvWriter.escape("=SUM(A1)")).isEqualTo("'=SUM(A1)");
        assertThat(CsvWriter.escape("+1")).isEqualTo("'+1");
        assertThat(CsvWriter.escape(-3)).isEqualTo("-3");
        assertThat(CsvWriter.escape(null)).isEmpty();
    }
}
