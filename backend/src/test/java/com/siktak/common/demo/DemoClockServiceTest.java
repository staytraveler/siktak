package com.siktak.common.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.siktak.common.error.BusinessException;
import com.siktak.common.settings.Setting;
import com.siktak.common.settings.SettingRepository;

class DemoClockServiceTest {

    private static final Instant REAL = Instant.parse("2026-10-17T05:00:00Z");

    private final DemoClock clock = new DemoClock(Clock.fixed(REAL, ZoneOffset.UTC));
    private final SettingRepository settings = mock(SettingRepository.class);
    private final DemoClockService service = service(true);

    private DemoClockService service(boolean enabled) {
        DemoClockService s = new DemoClockService(clock, settings);
        ReflectionTestUtils.setField(s, "enabled", enabled);
        return s;
    }

    @Test
    void 시계는_실제_시각에_offset을_더하고_시각을_읽을_때_DB에_접근하지_않는다() {
        clock.setOffset(Duration.ofDays(2));

        for (int i = 0; i < 100; i++) {
            assertThat(clock.instant()).isEqualTo(REAL.plus(Duration.ofDays(2)));
        }
        assertThat(clock.withZone(ZoneOffset.ofHours(9)).instant()).isEqualTo(REAL.plus(Duration.ofDays(2)));
        verifyNoInteractions(settings);
    }

    @Test
    void 기동_시_저장된_offset을_한_번_읽어온다() {
        given(settings.findById(DemoClockService.OFFSET_KEY))
                .willReturn(Optional.of(Setting.of(DemoClockService.OFFSET_KEY, Map.of("seconds", 3600), null)));

        service.load();

        assertThat(clock.instant()).isEqualTo(REAL.plusSeconds(3600));
    }

    @Test
    void 목표_시각으로_이동하면_offset을_저장하고_메모리에_반영한다() {
        given(settings.findById(any())).willReturn(Optional.empty());

        Instant moved = service.jumpTo(REAL.plus(Duration.ofDays(1)), 1L);

        assertThat(moved).isEqualTo(REAL.plus(Duration.ofDays(1)));
        assertThat(service.offset()).isEqualTo(Duration.ofDays(1));
        verify(settings).save(any(Setting.class));
    }

    @Test
    void 과거로는_이동할_수_없다() {
        clock.setOffset(Duration.ofDays(1));

        assertThatThrownBy(() -> service.jumpTo(REAL, 1L)).isInstanceOf(BusinessException.class);
        assertThat(service.offset()).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void 데모가_꺼져_있으면_offset은_0이고_바꿀_수_없다() {
        DemoClockService disabled = service(false);
        clock.setOffset(Duration.ofDays(3));

        disabled.load();

        assertThat(clock.instant()).isEqualTo(REAL);
        assertThatThrownBy(() -> disabled.advance(Duration.ofHours(1), 1L)).isInstanceOf(BusinessException.class);
    }
}
