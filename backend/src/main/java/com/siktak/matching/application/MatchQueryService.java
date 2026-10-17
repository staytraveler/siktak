package com.siktak.matching.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.application.domain.Application;
import com.siktak.application.domain.status.MatchStatus;
import com.siktak.common.config.ClockConfig;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.matching.domain.DiningTable;
import com.siktak.matching.domain.MatchDecision;
import com.siktak.matching.domain.MatchPlacementLog;
import com.siktak.matching.domain.MatchRun;
import com.siktak.matching.domain.TableMember;
import com.siktak.matching.domain.engine.ReasonCode;
import com.siktak.matching.infra.DiningTableRepository;
import com.siktak.matching.infra.MatchPlacementLogRepository;
import com.siktak.matching.infra.MatchRunRepository;
import com.siktak.matching.infra.TableMemberRepository;
import com.siktak.profile.domain.Interest;

import lombok.RequiredArgsConstructor;

/** 매칭 결과 조회 (운영자 자동 매칭 모니터, 참가자 매칭 근거). */
@Service
@RequiredArgsConstructor
public class MatchQueryService {

    /** matching.md 7-3 "적용된 기준" 문구. */
    public static final String CRITERIA_TEXT = "연령대·성비·관심사·대화 성향을 반영했어요";

    private final MatchRunRepository matchRunRepository;
    private final MatchPlacementLogRepository placementLogRepository;
    private final DiningTableRepository diningTableRepository;
    private final TableMemberRepository tableMemberRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<MatchRun> runs() {
        return matchRunRepository.findAllByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public MatchRun run(Long runId) {
        return matchRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "매칭 실행을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<MatchPlacementLog> logsOfRun(Long runId) {
        return placementLogRepository.findByMatchRunIdOrderByIdAsc(runId);
    }

    @Transactional(readOnly = true)
    public List<DiningTable> tablesOfRun(Long runId) {
        return diningTableRepository.findByMatchRunIdOrderByIdAsc(runId);
    }

    @Transactional(readOnly = true)
    public DiningTable table(Long tableId) {
        return diningTableRepository.findById(tableId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "테이블을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<TableMember> members(Long tableId) {
        return tableMemberRepository.findByDiningTableIdAndActiveTrue(tableId);
    }

    /**
     * 참가자에게 보여주는 매칭 근거 (matching.md 7-3, PRD MAT-12).
     * 다른 참가자의 개인 정보와 상세 점수는 넣지 않는다. 확정 전이면 상태와 안내 문구만.
     */
    @Transactional(readOnly = true)
    public Explanation explain(Application application) {
        List<MatchPlacementLog> logs = placementLogRepository.findByApplicationIdOrderByIdAsc(application.getId());
        Optional<TableMember> membership = tableMemberRepository.findByApplicationIdAndActiveTrue(application.getId());
        if (application.getMatchStatus() != MatchStatus.CONFIRMED || membership.isEmpty()) {
            String message = logs.isEmpty() ? null : logs.get(logs.size() - 1).getReasonText();
            return new Explanation(String.valueOf(application.getMatchStatus()), null, List.of(), null, null, null,
                    message);
        }
        DiningTable table = table(membership.get().getDiningTableId());
        Map<String, Object> summary = table.getSummary();

        List<String> interests = new ArrayList<>();
        if (summary.get("commonInterests") instanceof List<?> codes) {
            for (Object code : codes) {
                if (interests.size() == 2) {
                    break;
                }
                try {
                    interests.add(Interest.valueOf(String.valueOf(code)).label());
                } catch (IllegalArgumentException ignored) {
                    // 선택지 목록에 없는 값은 보여주지 않는다
                }
            }
        }
        String ageBand = null;
        if (summary.get("age") instanceof Map<?, ?> age && age.get("minBirthYear") instanceof Number min
                && age.get("maxBirthYear") instanceof Number max) {
            int year = clock.instant().atZone(ClockConfig.ZONE).getYear();
            ageBand = ageBand(year - max.intValue(), year - min.intValue());
        }
        String genderRatio = null;
        if (summary.get("gender") instanceof Map<?, ?> g && g.get("male") instanceof Number m
                && g.get("female") instanceof Number f) {
            genderRatio = "남 " + m.intValue() + " · 여 " + f.intValue();
        }
        String reallocation = logs.stream()
                .filter(l -> l.getDecision() == MatchDecision.CONFIRMED)
                .max(Comparator.comparing(MatchPlacementLog::getId))
                .filter(l -> !ReasonCode.PLACED_FIRST_CHOICE.name().equals(l.getReasonCode()))
                .map(MatchPlacementLog::getReasonText)
                .orElse(null);
        return new Explanation(MatchStatus.CONFIRMED.name(), CRITERIA_TEXT, interests, ageBand, genderRatio,
                reallocation, null);
    }

    /** 예: 27, 31 → "20대 후반~30대 초반". 만 나이 대신 연도 차이로 어림한다. */
    static String ageBand(int youngest, int oldest) {
        String low = band(youngest);
        String high = band(oldest);
        return low.equals(high) ? low : low + "~" + high;
    }

    private static String band(int age) {
        int decade = age / 10 * 10;
        int r = age % 10;
        return decade + "대 " + (r <= 3 ? "초반" : r <= 6 ? "중반" : "후반");
    }

    /**
     * @param criteria       적용된 기준 (확정일 때)
     * @param commonInterests 공통 관심사 상위 2개 (표시명)
     * @param reallocation   재배치를 거쳤다면 그 사유 문구
     * @param message        확정 전·실패 시 참가자 안내 문구
     */
    public record Explanation(String matchStatus, String criteria, List<String> commonInterests, String ageBand,
            String genderRatio, String reallocation, String message) {
    }
}
