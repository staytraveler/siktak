package com.siktak.common.demo;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.common.settings.Setting;
import com.siktak.common.settings.SettingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 데모 시계 offset 관리. 기동 시 1회 settings에서 적재하고, 바꿀 때만 저장한다 (매 now()마다 DB 조회 금지).
 * siktak.demo.enabled=false면 offset은 0으로 고정되고 변경할 수 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoClockService {

    public static final String OFFSET_KEY = "demo.clock-offset";

    private final DemoClock clock;
    private final SettingRepository settingRepository;

    @Value("${siktak.demo.enabled:true}")
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    /** 기동 시 1회 호출. */
    @Transactional(readOnly = true)
    public void load() {
        if (!enabled) {
            clock.setOffset(Duration.ZERO);
            return;
        }
        Duration offset = settingRepository.findById(OFFSET_KEY)
                .map(s -> Duration.ofSeconds(((Number) s.getValue().getOrDefault("seconds", 0)).longValue()))
                .orElse(Duration.ZERO);
        clock.setOffset(offset);
        log.info("Demo clock offset loaded: {}", offset);
    }

    public Instant now() {
        return clock.instant();
    }

    public Duration offset() {
        return clock.getOffset();
    }

    /** 데모 시각을 target으로 옮긴다. 과거로는 되돌릴 수 없다 (이미 실행된 작업과 어긋나므로). */
    @Transactional
    public Instant jumpTo(Instant target, Long operatorId) {
        requireEnabled();
        if (target.isBefore(clock.instant())) {
            throw new BusinessException(ErrorCode.CONFLICT, "데모 시각은 앞으로만 옮길 수 있습니다. 처음으로 돌리려면 시드를 초기화하세요.");
        }
        Duration offset = Duration.between(clock.realInstant(), target);
        save(offset, operatorId);
        clock.setOffset(offset);
        return clock.instant();
    }

    @Transactional
    public Instant advance(Duration amount, Long operatorId) {
        return jumpTo(clock.instant().plus(amount), operatorId);
    }

    /** 시드 초기화용: offset을 0으로 되돌린다. 호출 측 트랜잭션에 저장이 포함된다. */
    @Transactional
    public void reset(Long operatorId) {
        requireEnabled();
        save(Duration.ZERO, operatorId);
        clock.setOffset(Duration.ZERO);
    }

    private void save(Duration offset, Long operatorId) {
        Map<String, Object> value = Map.of("seconds", offset.toSeconds());
        settingRepository.findById(OFFSET_KEY).ifPresentOrElse(
                s -> s.change(value, operatorId),
                () -> settingRepository.save(Setting.of(OFFSET_KEY, value, operatorId)));
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }
}
