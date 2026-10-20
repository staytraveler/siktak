package com.siktak;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.chat.application.ChatRoomListService;
import com.siktak.common.demo.DemoProgressService;

/** 시연 가이드의 진행 판정 SQL과 대화방 목록 SQL이 실제 PostgreSQL에서 (데이터가 없어도) 돌아가는지. */
@Tag("db")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DemoProgressServiceIntegrationTest {

    @Autowired private DemoProgressService progressService;
    @Autowired private ChatRoomListService chatRoomListService;

    @Test
    void 진행_판정이_시드_유무와_관계없이_계산된다() {
        var p = progressService.progress();
        assertThat(p.stats()).isNotNull();
        assertThat(p.stats().autoActions()).isGreaterThanOrEqualTo(0);
        assertThat(p.jumps()).isNotNull();
    }

    @Test
    void 대화방_목록_조회가_된다() {
        assertThat(chatRoomListService.mine(-1L)).isEmpty();
    }
}
