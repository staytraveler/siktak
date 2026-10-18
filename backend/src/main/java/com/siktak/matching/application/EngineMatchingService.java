package com.siktak.matching.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.application.application.StatusTransitionService;
import com.siktak.application.domain.Application;
import com.siktak.application.domain.ApplicationSlotChoice;
import com.siktak.application.domain.PassType;
import com.siktak.application.domain.status.ActorType;
import com.siktak.application.domain.status.PaymentStatus;
import com.siktak.application.domain.status.Transition;
import com.siktak.application.domain.status.TransitionContext;
import com.siktak.chat.application.ChatService;
import com.siktak.chat.domain.ChatMessage;
import com.siktak.chat.domain.ChatRoom;
import com.siktak.common.config.ClockConfig;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.common.schedule.ScheduledJob;
import com.siktak.common.schedule.ScheduledJobRegistry;
import com.siktak.exception.application.ExceptionCaseService;
import com.siktak.exception.domain.CaseType;
import com.siktak.matching.domain.DiningTable;
import com.siktak.matching.domain.MatchFailureRecorded;
import com.siktak.matching.domain.MatchDecision;
import com.siktak.matching.domain.MatchPlacementLog;
import com.siktak.matching.domain.MatchResolution;
import com.siktak.matching.domain.MatchRun;
import com.siktak.matching.domain.TableGuidanceRequested;
import com.siktak.matching.domain.TableMember;
import com.siktak.matching.domain.TableMemberConfirmedEvent;
import com.siktak.matching.domain.alternative.AlternativeSlot;
import com.siktak.matching.domain.alternative.AlternativeSlotRecommender;
import com.siktak.matching.domain.alternative.SlotOption;
import com.siktak.matching.domain.engine.Candidate;
import com.siktak.matching.domain.engine.ExcludedCandidate;
import com.siktak.matching.domain.engine.MatchingConfig;
import com.siktak.matching.domain.engine.MatchingEngine;
import com.siktak.matching.domain.engine.MatchingInput;
import com.siktak.matching.domain.engine.MatchingResult;
import com.siktak.matching.domain.engine.PlacementDecision;
import com.siktak.matching.domain.engine.PlacementLog;
import com.siktak.matching.domain.engine.ProposedTable;
import com.siktak.matching.domain.engine.ReasonCode;
import com.siktak.matching.domain.engine.SlotInfo;
import com.siktak.matching.domain.engine.UnplacedCandidate;
import com.siktak.matching.domain.engine.UserPair;
import com.siktak.matching.domain.engine.score.ScoreDetail;
import com.siktak.matching.infra.DiningTableRepository;
import com.siktak.matching.infra.MatchPlacementLogRepository;
import com.siktak.matching.infra.MatchResolutionRepository;
import com.siktak.matching.infra.MatchRunRepository;
import com.siktak.matching.infra.MatchingApplicationQuery;
import com.siktak.matching.infra.MatchingDataReader;
import com.siktak.matching.infra.MatchingDataReader.UserProfile;
import com.siktak.matching.infra.TableMemberRepository;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.domain.SlotStatus;
import com.siktak.slot.infra.EventSlotRepository;
import com.siktak.venue.application.VenueAssignmentService;
import com.siktak.venue.domain.assign.TableToAssign;
import com.siktak.venue.domain.assign.VenueAssignment;
import com.siktak.venue.domain.assign.VenueAssignmentResult;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * 매칭 엔진(v2) 운영 연결 (4.4, MAT-01·07·08·10·11·12·13, INF-02).
 *
 * <p>한 번의 실행은 매칭 시각이 도래한 회차 전부를 함께 다룬다 (matching.md 5-0). 그래야 같은 날 다른 지역 재배치(2단계)가 된다.
 * run_key는 대상 회차 ID 목록의 해시라, 같은 대상으로 다시 부르면 기존 실행을 돌려준다 (E20).
 * 이미 매칭된 회차로 부르면 그 회차를 포함한 실행을 돌려준다 (스케줄러가 회차마다 호출해도 한 번만 실행).
 */
@Service
@RequiredArgsConstructor
public class EngineMatchingService {

    public static final String ALGORITHM = "engine-" + MatchingEngine.ALGORITHM_VERSION;
    private static final ZoneId ZONE = ClockConfig.ZONE;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final EventSlotRepository eventSlotRepository;
    private final MatchingApplicationQuery applicationQuery;
    private final MatchingDataReader dataReader;
    private final MatchRunRepository matchRunRepository;
    private final DiningTableRepository diningTableRepository;
    private final TableMemberRepository tableMemberRepository;
    private final MatchPlacementLogRepository placementLogRepository;
    private final MatchResolutionRepository resolutionRepository;
    private final StatusTransitionService transitionService;
    private final ChatService chatService;
    private final VenueAssignmentService venueAssignmentService;
    private final ExceptionCaseService exceptionCaseService;
    private final ScheduledJobRegistry jobRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    private final MatchingEngine engine = new MatchingEngine();

    /**
     * @param operatorId 운영자 버튼이면 운영자 ID, 스케줄러면 {@code null}
     */
    @Transactional
    public MatchRun runForSlot(Long slotId, Long operatorId) {
        Instant now = Instant.now(clock);
        List<EventSlot> allSlots = eventSlotRepository.findAllByOrderByStartsAtAscIdAsc();
        Map<Long, EventSlot> slotById = allSlots.stream()
                .collect(Collectors.toMap(EventSlot::getId, Function.identity()));
        EventSlot trigger = slotById.get(slotId);
        if (trigger == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "회차를 찾을 수 없습니다.");
        }
        if (!pending(trigger)) {
            return matchRunRepository.findLatestCoveringSlot(slotId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT,
                            "매칭할 수 없는 회차 상태입니다: " + trigger.getSlotStatus()));
        }

        List<EventSlot> targets = allSlots.stream()
                .filter(s -> pending(s) && (s.getId().equals(slotId) || !s.getMatchRunAt().isAfter(now)))
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .toList();
        List<Long> targetIds = targets.stream().map(EventSlot::getId).toList();
        String runKey = runKey(targetIds);
        Optional<MatchRun> existing = matchRunRepository.findByRunKey(runKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        // 5-1 후보 추출: 프로필이 없으면 매칭할 수 없어 예외로 넘긴다 (MAT-13, DATA_MISSING).
        List<Application> applications = applicationQuery.findCandidates(targetIds).stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .toList();
        Map<Long, UserProfile> profiles = dataReader.profiles(
                applications.stream().map(Application::getUserId).distinct().toList());
        List<Application> missingProfile = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        for (Application a : applications) {
            UserProfile p = profiles.get(a.getUserId());
            if (p == null || !p.complete()) {
                missingProfile.add(a);
                continue;
            }
            candidates.add(toCandidate(a, p, targetIds, slotById, now));
        }
        List<Long> userIds = candidates.stream().map(Candidate::userId).distinct().toList();
        Set<UserPair> blocked = dataReader.blockedPairs(userIds);
        Set<UserPair> encounters = dataReader.encounters(userIds);
        MatchingConfig config = dataReader.config();

        List<SlotInfo> slotInfos = targets.stream()
                .map(s -> new SlotInfo(s.getId(), dateOf(s), s.getRegion().getId()))
                .toList();
        MatchingResult result = engine.run(new MatchingInput(slotInfos, candidates, blocked, encounters, config));

        MatchRun run = matchRunRepository.save(MatchRun.start(runKey,
                slotInfos.stream().map(SlotInfo::date).min(LocalDate::compareTo).orElse(dateOf(trigger)),
                ALGORITHM, params(targetIds, operatorId, config), candidates.size(), now));
        Context ctx = new Context(run, operatorId, now,
                applications.stream().collect(Collectors.toMap(Application::getId, Function.identity())));

        for (Application a : missingProfile) {
            exceptionCaseService.open(CaseType.DATA_MISSING, "DATA_MISSING:application-" + a.getId(), a.getId(), null,
                    "매칭 프로필(출생연도·성별)이 없어 자동 매칭에서 제외했습니다.", Map.of("matchRunId", run.getId()));
        }

        // M2: 엔진이 후보로 받아들인 신청은 모두 MATCHING을 거친다.
        Set<Long> excludedIds = result.excluded().stream().map(ExcludedCandidate::applicationId)
                .collect(Collectors.toSet());
        TransitionContext scheduler = new TransitionContext(ActorType.SCHEDULER, operatorId,
                operatorId == null ? "매칭 마감 시각 도래, 자동 매칭 실행" : "운영자가 매칭 실행 (스케줄러 대신)", run.getId());
        for (Candidate c : candidates) {
            if (!excludedIds.contains(c.applicationId())) {
                transitionService.apply(ctx.app(c.applicationId()), Transition.M2, scheduler);
            }
        }

        Map<Long, PlacementLog> confirmedLogs = new HashMap<>();
        for (PlacementLog l : result.placementLogs()) {
            if (l.decision() == PlacementDecision.CONFIRMED) {
                confirmedLogs.put(l.applicationId(), l);
            }
        }
        Map<Long, Long> tableOfApplication = saveTables(ctx, result.tables(), confirmedLogs, profiles);
        for (UnplacedCandidate u : result.deferred()) {
            deferApplication(ctx, u);
        }
        for (UnplacedCandidate u : result.failed()) {
            failApplication(ctx, u, allSlots, config);
        }
        for (ExcludedCandidate e : result.excluded()) {
            if (e.reasonCode() == ReasonCode.ACCOUNT_RESTRICTED) {
                // [해석] 계정 제한(H2)은 안전 조치에 해당해 SAFETY_REPORT로 남긴다 (matching.md 5-1 "DATA_MISSING 또는 SAFETY").
                exceptionCaseService.open(CaseType.SAFETY_REPORT, "ACCOUNT_RESTRICTED:application-" + e.applicationId(),
                        e.applicationId(), null, "계정 제한 상태라 매칭 후보에서 제외했습니다.",
                        Map.of("matchRunId", run.getId(), "userId", e.userId()));
            }
            // NOT_PAID는 상태를 바꾸지 않고 로그만 남긴다.
        }
        for (PlacementLog l : result.placementLogs()) {
            placementLogRepository.save(MatchPlacementLog.fromEngine(run.getId(), l.applicationId(),
                    MatchDecision.valueOf(l.decision().name()), l.fromSlotId(), l.toSlotId(),
                    l.decision() == PlacementDecision.CONFIRMED ? tableOfApplication.get(l.applicationId()) : null,
                    l.reallocStep(), l.reasonCode().name(), l.causeCode() == null ? null : l.causeCode().name(),
                    l.reasonText()));
        }
        targets.forEach(EventSlot::markMatched);

        int[] venueCounts = assignVenues(ctx, targets, profiles);
        writeFailReasons(ctx);
        for (UnplacedCandidate u : result.failed()) {
            eventPublisher.publishEvent(new MatchFailureRecorded(u.applicationId()));
        }
        for (Long tableId : new TreeSet<>(tableOfApplication.values())) {
            eventPublisher.publishEvent(new TableGuidanceRequested(tableId));
        }

        Map<String, Object> summary = new LinkedHashMap<>(JSON.convertValue(result.summary(), Map.class));
        summary.put("slotIds", targetIds);
        summary.put("dataMissing", missingProfile.size());
        summary.put("venueAssigned", venueCounts[0]);
        summary.put("venueUnavailable", venueCounts[1]);
        run.complete(summary, Instant.now(clock));
        return run;
    }

    /** 매칭 대기 중인 회차 (아직 매칭 안 됨). */
    private static boolean pending(EventSlot s) {
        return s.getSlotStatus() == SlotStatus.OPEN || s.getSlotStatus() == SlotStatus.CLOSED;
    }

    private static LocalDate dateOf(EventSlot s) {
        return s.getStartsAt().atZone(ZONE).toLocalDate();
    }

    /** matching.md 5-0: run_key = 대상 회차 ID 목록의 해시. */
    static String runKey(List<Long> sortedSlotIds) {
        String joined = sortedSlotIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8));
            return ALGORITHM + ":slots-" + HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 엔진 후보. 선택 회차 중 이미 지나간(매칭 끝난·마감 시각 지난) 회차는 빼야 엔진이 "남은 선택 회차"를 바르게 판단한다
     * (예: J2에서 user14의 S1은 이미 지난 회차라 S3 실패 시 대기가 아니라 실패).
     */
    private static Candidate toCandidate(Application a, UserProfile p, List<Long> targetIds,
            Map<Long, EventSlot> slotById, Instant now) {
        List<Long> choices = a.getSlotChoicesByPriority().stream()
                .map(ApplicationSlotChoice::getEventSlotId)
                .filter(id -> targetIds.contains(id) || future(slotById.get(id), now))
                .toList();
        return new Candidate(a.getId(), a.getUserId(), p.birthYear(), p.gender(), p.mbti(), p.interests(),
                p.selfTraits(), p.preferredTraits(), choices, p.accountActive(),
                a.getPaymentStatus() == PaymentStatus.PAID);
    }

    private static boolean future(EventSlot s, Instant now) {
        return s != null && pending(s) && s.getMatchRunAt().isAfter(now);
    }

    private static Map<String, Object> params(List<Long> slotIds, Long operatorId, MatchingConfig config) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("slotIds", slotIds);
        params.put("trigger", operatorId == null ? "SCHEDULER" : "OPERATOR_BUTTON");
        params.put("operatorId", operatorId);
        params.put("config", JSON.convertValue(config, Map.class));
        return params;
    }

    // ------------------------------------------------------------------ 확정

    private Map<Long, Long> saveTables(Context ctx, List<ProposedTable> tables, Map<Long, PlacementLog> confirmedLogs,
            Map<Long, UserProfile> profiles) {
        Map<Long, Long> tableOfApplication = new HashMap<>();
        for (ProposedTable p : tables) {
            DiningTable table = diningTableRepository.save(DiningTable.confirmedByEngine(p.slotId(), ctx.run.getId(),
                    p.members().size(), BigDecimal.valueOf(p.totalScore()).setScale(2, RoundingMode.HALF_UP),
                    scoreDetail(p.scoreDetail()), JSON.convertValue(p.summary(), Map.class), ctx.now));
            List<Long> userIds = new ArrayList<>();
            for (Long applicationId : p.members()) {
                Application a = ctx.app(applicationId);
                PlacementLog log = confirmedLogs.get(applicationId);
                boolean reallocated = log != null && log.reallocStep() != null;
                if (reallocated) {
                    // M4 → M5: 재배치를 거쳐 확정 (MAT-08)
                    transitionService.apply(a, Transition.M4, ctx.system(
                            "최초 배치 실패(" + log.causeCode() + "), 자동 재배치 " + log.reallocStep() + "단계"));
                    transitionService.apply(a, Transition.M5, ctx.system(log.reasonText()));
                } else {
                    transitionService.apply(a, Transition.M3, ctx.system("필수 조건·최소 점수 충족, 자동 확정"));
                }
                a.assignSlot(p.slotId());
                tableMemberRepository.save(TableMember.join(table.getId(), applicationId, a.getUserId(), ctx.now));
                events.publishEvent(new TableMemberConfirmedEvent(table.getId(), applicationId, a.getUserId()));
                tableOfApplication.put(applicationId, table.getId());
                userIds.add(a.getUserId());
            }
            ChatRoom room = chatService.openForTable(table.getId(), userIds);
            chatService.postSystemMessage(room.getId(), ChatMessage.MessageType.NOTICE,
                    "테이블이 확정됐어요. 식당과 시간 안내는 곧 이 방에 올라옵니다.",
                    "table-" + table.getId() + ":confirmed", null);
        }
        return tableOfApplication;
    }

    private static Map<String, Object> scoreDetail(ScoreDetail detail) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ScoreDetail.Item i : detail.items()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", i.code().name());
            item.put("label", i.code().label());
            item.put("raw", i.raw());
            item.put("weight", i.weight());
            item.put("weighted", i.weighted());
            items.add(item);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("total", detail.total());
        map.put("items", items);
        return map;
    }

    // ------------------------------------------------------------------ 미배치

    /** M4a: 매칭 시각이 오지 않은 선택 회차가 남아 WAITING 복귀 (matching.md 5-8). */
    private void deferApplication(Context ctx, UnplacedCandidate u) {
        Application a = ctx.app(u.applicationId());
        transitionService.apply(a, Transition.M4a, ctx.system(
                "이번 매칭에서 미배치(" + u.causeCode() + "), 남은 선택 회차에서 다시 매칭"));
        ctx.failReasons.put(a.getId(), ReasonCode.DEFERRED.text());
    }

    /** M4 → M6 (+ 1회권이면 P5), 대체 일정 추천 (MAT-11, STA-03·04). */
    private void failApplication(Context ctx, UnplacedCandidate u, List<EventSlot> allSlots, MatchingConfig config) {
        Application a = ctx.app(u.applicationId());
        transitionService.apply(a, Transition.M4, ctx.system("최초 배치 실패(" + u.causeCode() + "), 자동 재배치 시도"));
        transitionService.apply(a, Transition.M6, ctx.system("재배치 1~4단계 모두 실패(" + u.reasonCode() + ")"));
        if (a.getPassType() == PassType.SINGLE && a.getPaymentStatus() == PaymentStatus.PAID) {
            transitionService.apply(a, Transition.P5, ctx.system("매칭 실패로 1회권 환불 가능"));
        }
        ctx.failReasons.put(a.getId(), u.reasonCode().text());

        if (resolutionRepository.findByApplicationId(a.getId()).isEmpty()) {
            List<Long> chosen = a.getSlotChoicesByPriority().stream().map(ApplicationSlotChoice::getEventSlotId).toList();
            List<SlotOption> options = allSlots.stream()
                    .map(s -> new SlotOption(s.getId(), dateOf(s), s.getRegion().getId(),
                            s.getSlotStatus() == SlotStatus.OPEN, s.getMatchRunAt()))
                    .toList();
            List<Long> offered = AlternativeSlotRecommender.recommend(chosen, options, ctx.now,
                    config.alternativeCount()).stream().map(AlternativeSlot::slotId).toList();
            // respond_by = 원래 1순위 회차 행사 시작 시각 (미응답 시 보관, D6)
            Instant respondBy = allSlots.stream().filter(s -> !chosen.isEmpty() && s.getId().equals(chosen.get(0)))
                    .map(EventSlot::getStartsAt).findFirst().orElse(ctx.now);
            MatchResolution resolution = resolutionRepository.save(MatchResolution.offer(a.getId(), offered, respondBy));
            // 미응답 시 보관 처리 (M8a) — 커밋 뒤 작업 실행기에 등록
            jobRegistry.schedule(new ScheduledJob(ResolutionService.TIMEOUT_JOB, resolution.getId(), respondBy));
        }
    }

    // ------------------------------------------------------------------ 식당 배정

    /** 확정 직후 회차별 식당 배정 (INF-02). @return [배정 수, 미배정 수] */
    private int[] assignVenues(Context ctx, List<EventSlot> targets, Map<Long, UserProfile> profiles) {
        int assigned = 0;
        int unassigned = 0;
        for (EventSlot slot : targets) {
            List<DiningTable> tables = diningTableRepository.findByEventSlotIdOrderByIdAsc(slot.getId()).stream()
                    .filter(t -> t.getMatchRunId().equals(ctx.run.getId()))
                    .toList();
            if (tables.isEmpty()) {
                continue;
            }
            List<TableToAssign> requests = tables.stream()
                    .map(t -> tableToAssign(t, profiles))
                    .toList();
            VenueAssignmentResult result = venueAssignmentService.assign(slot.getId(), slot.getRegion().getId(),
                    dateOf(slot), requests);
            Map<Long, DiningTable> byId = tables.stream().collect(Collectors.toMap(DiningTable::getId, t -> t));
            for (VenueAssignment va : result.assigned()) {
                DiningTable t = byId.get(va.tableId());
                t.proposeReservationName(va.reservationName());
                t.assignVenue(va.venueId(), ctx.now);
                assigned++;
            }
            for (var u : result.unassigned()) {
                byId.get(u.tableId()).proposeReservationName(u.reservationName());
                unassigned++;
            }
        }
        return new int[] {assigned, unassigned};
    }

    TableToAssign tableToAssign(DiningTable table, Map<Long, UserProfile> profiles) {
        List<String> prices = tableMemberRepository.findByDiningTableIdAndActiveTrue(table.getId()).stream()
                .map(m -> profiles.containsKey(m.getUserId()) ? profiles.get(m.getUserId()).priceRange() : null)
                .toList();
        return new TableToAssign(table.getId(), table.getMemberCount(), prices);
    }

    /** 참가자 노출 문구(matching.md 7-2)를 {@code applications.match_fail_reason}에 쓴다. */
    private void writeFailReasons(Context ctx) {
        ctx.failReasons.forEach((applicationId, reason) -> ctx.app(applicationId).recordMatchFailReason(reason));
    }

    private static final class Context {
        final MatchRun run;
        final Long operatorId;
        final Instant now;
        final Map<Long, Application> applications;
        final Map<Long, String> failReasons = new TreeMap<>();

        Context(MatchRun run, Long operatorId, Instant now, Map<Long, Application> applications) {
            this.run = run;
            this.operatorId = operatorId;
            this.now = now;
            this.applications = applications;
        }

        Application app(Long id) {
            return applications.get(id);
        }

        TransitionContext system(String reason) {
            return new TransitionContext(ActorType.SYSTEM, null, reason, run.getId());
        }
    }
}
