package com.siktak.admin.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.common.settings.Setting;
import com.siktak.common.settings.SettingRepository;
import com.siktak.matching.domain.engine.MatchingConfig;
import com.siktak.matching.domain.engine.score.ScoreCode;

import lombok.RequiredArgsConstructor;

/**
 * 매칭 가중치·최소 점수 설정 (MAT-15, 8.2). {@code settings}의 {@code matching.weights}·{@code matching.min-score}에
 * 저장하고, 매칭 실행 때 {@code MatchingDataReader.config()}가 읽는다 — 다음 실행부터 적용된다.
 * 숫자 설정은 settings.value가 JSON 객체여서 {@code {"value": n}} 형태로 둔다.
 */
@Service
@RequiredArgsConstructor
public class MatchingSettingsService {

    static final String WEIGHTS_KEY = "matching.weights";
    static final String MIN_SCORE_KEY = "matching.min-score";

    private final SettingRepository settingRepository;

    public record MatchingSettings(Map<String, Double> weights, double minScore) {
    }

    @Transactional(readOnly = true)
    public MatchingSettings get() {
        MatchingConfig defaults = MatchingConfig.defaults();
        Map<String, Object> saved = settingRepository.findById(WEIGHTS_KEY).map(Setting::getValue).orElse(Map.of());
        Map<String, Double> weights = new LinkedHashMap<>();
        for (ScoreCode code : ScoreCode.values()) {
            Object v = saved.get(code.name());
            weights.put(code.name(), v instanceof Number n ? n.doubleValue() : defaults.weights().of(code));
        }
        Object min = settingRepository.findById(MIN_SCORE_KEY).map(s -> s.getValue().get("value")).orElse(null);
        return new MatchingSettings(weights, min instanceof Number n ? n.doubleValue() : defaults.minScore());
    }

    @Transactional
    public MatchingSettings update(Map<String, Double> weights, double minScore, Long operatorId) {
        double sum = 0;
        Map<String, Object> value = new LinkedHashMap<>();
        for (ScoreCode code : ScoreCode.values()) {
            Double w = weights.get(code.name());
            if (w == null || w < 0 || w > 100) {
                throw invalid(code.name() + " 가중치는 0~100 사이여야 합니다.");
            }
            sum += w;
            value.put(code.name(), w);
        }
        if (Math.abs(sum - 100) > 0.0001) {
            throw invalid("가중치 합계는 100이어야 합니다. (현재 " + sum + ")");
        }
        if (minScore < 0 || minScore > 100) {
            throw invalid("최소 점수는 0~100 사이여야 합니다.");
        }
        save(WEIGHTS_KEY, value, operatorId);
        save(MIN_SCORE_KEY, Map.of("value", minScore), operatorId);
        return get();
    }

    public static List<String> codes() {
        return java.util.Arrays.stream(ScoreCode.values()).map(Enum::name).toList();
    }

    private void save(String key, Map<String, Object> value, Long operatorId) {
        settingRepository.findById(key).ifPresentOrElse(s -> s.change(value, operatorId),
                () -> settingRepository.save(Setting.of(key, value, operatorId)));
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_REQUEST, message);
    }
}
