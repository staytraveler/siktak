package com.siktak.common.demo;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.common.schedule.JobTrigger;
import com.siktak.common.schedule.ScheduledJob;
import com.siktak.common.schedule.ScheduledJobRegistry;
import com.siktak.common.schedule.ScheduledJobRunner;
import com.siktak.common.seed.DemoSeedService;
import com.siktak.common.settings.Setting;
import com.siktak.common.settings.SettingRepository;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.infra.EventSlotRepository;

import lombok.RequiredArgsConstructor;

/**
 * 데모 도구 (docs/demo-scenario.md 1장). 시계 이동은 offset만 바꾸고, 이동 직후 실행기를 한 번 돌려
 * 도래한 작업(예: J1 → S1·S2 매칭)이 바로 실행되게 한다. 작업 로직은 실제 스케줄러와 같은 코드다.
 * 트랜잭션은 각 하위 서비스가 갖는다 (시드 커밋 후 실행기 재적재).
 */
@Service
@RequiredArgsConstructor
public class DemoService {

    private final DemoClockService clockService;
    private final DemoSeedService seedService;
    private final ScheduledJobRunner jobRunner;
    private final ScheduledJobRegistry jobRegistry;
    private final SettingRepository settingRepository;
    private final EventSlotRepository eventSlotRepository;

    /** J1~J6 점프 대상 (demo-scenario 1장). J3~J5는 아직 해당 작업이 없어 시각 이동만 된다. */
    public enum Jump {
        J1("1회차 매칭 마감 (S1·S2)"),
        J2("S3 매칭 마감"),
        J3("행사 1일 전 (리마인드)"),
        J4("행사 시작 (대화 콘텐츠·체크인)"),
        J5("행사 종료 (노쇼 후보·피드백 요청)"),
        J6("2회차 매칭 마감 (S4·S5)");

        private final String label;

        Jump(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public DemoStatus status() {
        requireEnabled();
        Instant now = clockService.now();
        Map<String, Object> seed = seedInfo();
        List<JumpTarget> jumps = new ArrayList<>();
        if (!seed.isEmpty()) {
            for (Jump jump : Jump.values()) {
                Instant at = targetOf(jump);
                jumps.add(new JumpTarget(jump.name(), jump.label(), at, !at.isAfter(now)));
            }
        }
        return new DemoStatus(now, clockService.offset().toSeconds(), (String) seed.get("t0"),
                (String) seed.get("version"), jumps, jobRegistry.upcoming(10));
    }

    public DemoStatus reset(Long operatorId) {
        requireEnabled();
        seedService.reset(operatorId);
        jobRunner.reloadAndCatchUp();
        return status();
    }

    public DemoStatus jump(Jump jump, Long operatorId) {
        requireEnabled();
        Instant target = targetOf(jump);
        clockService.jumpTo(target, operatorId);
        jobRunner.runDue(JobTrigger.byOperator(operatorId, "데모 도구 " + jump.name() + " 점프"));
        return status();
    }

    public DemoStatus advance(Duration amount, Long operatorId) {
        requireEnabled();
        clockService.advance(amount, operatorId);
        jobRunner.runDue(JobTrigger.byOperator(operatorId, "데모 도구 시간 앞당기기 +" + amount));
        return status();
    }

    private Instant targetOf(Jump jump) {
        return switch (jump) {
            case J1 -> slot("S1").getMatchRunAt();
            case J2 -> slot("S3").getMatchRunAt();
            case J3 -> slot("S1").getStartsAt().minus(Duration.ofDays(1));
            case J4 -> slot("S1").getStartsAt();
            case J5 -> slot("S1").getEndsAt();
            case J6 -> slot("S4").getMatchRunAt();
        };
    }

    @SuppressWarnings("unchecked")
    private EventSlot slot(String code) {
        Map<String, Object> slots = (Map<String, Object>) seedInfo().get("slots");
        if (slots == null || !slots.containsKey(code)) {
            throw new BusinessException(ErrorCode.CONFLICT, "demo-v1 시드가 없습니다. 시드를 초기화하세요.");
        }
        long id = ((Number) slots.get(code)).longValue();
        return eventSlotRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "시드 회차가 없습니다. 시드를 초기화하세요."));
    }

    private Map<String, Object> seedInfo() {
        return settingRepository.findById(DemoSeedService.SEED_KEY).map(Setting::getValue)
                .map(v -> (Map<String, Object>) new LinkedHashMap<>(v)).orElse(Map.of());
    }

    private void requireEnabled() {
        if (!clockService.isEnabled()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    public record DemoStatus(Instant now, long offsetSeconds, String t0, String seedVersion, List<JumpTarget> jumps,
            List<ScheduledJob> upcomingJobs) {
    }

    public record JumpTarget(String code, String label, Instant at, boolean reached) {
    }
}
