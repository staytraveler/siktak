package com.siktak.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.siktak.common.error.BusinessException;
import com.siktak.common.settings.Setting;
import com.siktak.common.settings.SettingRepository;

class MatchingSettingsServiceTest {

    private final SettingRepository repository = mock(SettingRepository.class);
    private final MatchingSettingsService service = new MatchingSettingsService(repository);

    private static Map<String, Double> weights(double... w) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < w.length; i++) {
            m.put("S" + (i + 1), w[i]);
        }
        return m;
    }

    @Test
    void 저장된_값이_없으면_matching_md_기본값() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        var settings = service.get();
        assertThat(settings.weights()).containsEntry("S1", 20.0).containsEntry("S6", 12.0);
        assertThat(settings.minScore()).isEqualTo(55);
    }

    @Test
    void 합이_100이_아니면_저장하지_않는다() {
        assertThatThrownBy(() -> service.update(weights(20, 20, 18, 16, 14, 13), 55, 1L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("100");
        verify(repository, never()).save(any());
    }

    @Test
    void 음수_가중치와_범위_밖_최소점수는_거부() {
        assertThatThrownBy(() -> service.update(weights(-5, 25, 18, 16, 24, 22), 55, 1L))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.update(weights(20, 20, 18, 16, 14, 12), 101, 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 정상_값은_가중치와_최소점수를_저장한다() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        service.update(weights(30, 20, 15, 15, 10, 10), 60, 7L);
        verify(repository, org.mockito.Mockito.times(2)).save(any(Setting.class));
    }
}
