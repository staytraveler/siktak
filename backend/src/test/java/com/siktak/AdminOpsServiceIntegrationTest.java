package com.siktak;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.admin.application.AdminOpsService;

/** 운영자 결제·환불·자동 메시지 조회 SQL이 실제 PostgreSQL에서 필터 유무와 관계없이 돌아가는지 (null 파라미터 타입 오류 회귀). */
@Tag("db")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminOpsServiceIntegrationTest {

    @Autowired private AdminOpsService opsService;

    @Test
    void 필터가_있든_없든_조회가_된다() {
        assertThat(opsService.payments(null)).isNotNull();
        assertThat(opsService.payments("PAID")).isNotNull();
        assertThat(opsService.history(null)).isNotNull();
        assertThat(opsService.history("FAILED")).isNotNull();
        assertThat(opsService.passes()).isNotNull();
        assertThat(opsService.templates()).isNotEmpty();
    }

    @Test
    void 템플릿을_고치고_끌_수_있다() {
        var original = opsService.templates().get(0);
        var updated = opsService.updateTemplate(original.code(), "새 제목", "새 본문 {{slotLabel}}", false);
        assertThat(updated.title()).isEqualTo("새 제목");
        assertThat(updated.active()).isFalse();
        assertThat(opsService.templates()).anyMatch(t -> t.code().equals(original.code()) && !t.active());
    }
}
