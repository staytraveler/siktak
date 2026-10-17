package com.siktak.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.application.application.ApplicationService;
import com.siktak.application.domain.Application;
import com.siktak.application.domain.PassType;
import com.siktak.application.domain.status.MatchStatus;
import com.siktak.application.domain.status.PaymentStatus;
import com.siktak.application.domain.status.StatusHistory;
import com.siktak.application.infra.StatusHistoryRepository;
import com.siktak.common.config.ClockConfig;
import com.siktak.common.error.BusinessException;
import com.siktak.exception.domain.CaseStatus;
import com.siktak.exception.domain.CaseType;
import com.siktak.exception.domain.ExceptionCase;
import com.siktak.exception.infra.ExceptionCaseRepository;
import com.siktak.matching.application.MatchQueryService;
import com.siktak.matching.application.MatchingService;
import com.siktak.matching.application.TableVenueService;
import com.siktak.matching.domain.DiningTable;
import com.siktak.matching.domain.MatchDecision;
import com.siktak.matching.domain.MatchPlacementLog;
import com.siktak.matching.domain.MatchResolution;
import com.siktak.matching.domain.MatchRun;
import com.siktak.matching.domain.TableMember;
import com.siktak.matching.domain.engine.Candidate;
import com.siktak.matching.domain.engine.DemoSeed;
import com.siktak.matching.infra.DiningTableRepository;
import com.siktak.matching.infra.MatchPlacementLogRepository;
import com.siktak.matching.infra.MatchResolutionRepository;
import com.siktak.matching.infra.TableMemberRepository;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.domain.Region;
import com.siktak.slot.infra.EventSlotRepository;
import com.siktak.slot.infra.RegionRepository;
import com.siktak.user.domain.User;
import com.siktak.user.domain.UserRole;
import com.siktak.user.infra.UserRepository;
import com.siktak.content.application.ConversationContentSeeder;
import com.siktak.venue.application.DemoVenueSeed;
import com.siktak.venue.domain.Venue;
import com.siktak.venue.infra.VenueSlotCapacityRepository;

/**
 * 매칭 엔진 운영 연결 (4.4): demo-scenario.md 시드로 J1·J2를 실제 DB에서 돌려 4장 기대 결과와 비교한다.
 * 시각은 데모 도구처럼 테스트 시계를 옮겨서 진행하고, 트랜잭션 롤백으로 데이터를 남기지 않는다.
 */
@Tag("db")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest
@ActiveProfiles("test")
@Import(EngineMatchingIntegrationTest.MovableClockConfig.class)
@Transactional
class EngineMatchingIntegrationTest {

    private static final ZoneId ZONE = ClockConfig.ZONE;

    @TestConfiguration
    static class MovableClockConfig {
        @Bean
        @Primary
        MovableClock movableClock() {
            return new MovableClock(Instant.parse("2026-10-17T03:00:00Z"));
        }
    }

    /** 데모 시계처럼 앞으로만 옮기는 테스트 시계. */
    static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        void set(Instant at) {
            this.now = at;
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Autowired private MovableClock clock;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository userRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private EventSlotRepository eventSlotRepository;
    @Autowired private ApplicationService applicationService;
    @Autowired private MatchingService matchingService;
    @Autowired private TableVenueService tableVenueService;
    @Autowired private MatchQueryService matchQueryService;
    @Autowired private DemoVenueSeed demoVenueSeed;
    @Autowired private ConversationContentSeeder contentSeeder;
    @Autowired private VenueSlotCapacityRepository capacityRepository;
    @Autowired private DiningTableRepository diningTableRepository;
    @Autowired private TableMemberRepository tableMemberRepository;
    @Autowired private MatchPlacementLogRepository placementLogRepository;
    @Autowired private MatchResolutionRepository resolutionRepository;
    @Autowired private ExceptionCaseRepository exceptionCaseRepository;
    @Autowired private StatusHistoryRepository statusHistoryRepository;

    private EventSlot s1;
    private EventSlot s2;
    private EventSlot s3;
    private EventSlot s4;
    private EventSlot s5;
    private Venue oak;
    private Venue green;
    private Venue mono;
    private Long operatorId;
    /** userNN 번호 → 신청 */
    private final Map<Integer, Application> app = new HashMap<>();

    /** demo-scenario.md 2·3장: T0 기준 D = T0 + 3일, S1·S2 매칭 = D−2일 12:00, S3 = D−1일 12:00, S4·S5 = D+5일 12:00. */
    @BeforeEach
    void seed() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Instant t0 = Instant.parse("2026-10-17T03:00:00Z");
        clock.set(t0);
        var d = t0.atZone(ZONE).toLocalDate().plusDays(3);
        Region seongsu = regionRepository.save(Region.create("성수-" + tag));
        Region hongdae = regionRepository.save(Region.create("홍대합정-" + tag));
        Region gangnam = regionRepository.save(Region.create("강남-" + tag));
        s1 = slot(seongsu, d.atTime(19, 30), d.minusDays(2).atTime(12, 0));
        s2 = slot(hongdae, d.atTime(19, 30), d.minusDays(2).atTime(12, 0));
        s3 = slot(seongsu, d.plusDays(1).atTime(19, 30), d.minusDays(1).atTime(12, 0));
        s4 = slot(seongsu, d.plusDays(7).atTime(19, 30), d.plusDays(5).atTime(12, 0));
        s5 = slot(gangnam, d.plusDays(7).atTime(19, 30), d.plusDays(5).atTime(12, 0));

        List<Venue> venues = demoVenueSeed.seed(seongsu.getId(), hongdae.getId(), gangnam.getId(), Map.of(
                "S1", s1.getId(), "S2", s2.getId(), "S3", s3.getId(), "S4", s4.getId(), "S5", s5.getId()));
        oak = venues.get(0);
        green = venues.get(1);
        mono = venues.get(2);

        operatorId = userRepository.save(User.create("admin-" + tag, UserRole.OPERATOR, "운영자", null)).getId();
        Map<Long, Long> slotIds = Map.of(DemoSeed.S1, s1.getId(), DemoSeed.S2, s2.getId(), DemoSeed.S3, s3.getId());
        for (Candidate c : DemoSeed.j1Input().candidates()) {
            int no = (int) c.applicationId();
            Long userId = participant(tag, no, c);
            Application a = applicationService.submit(userId,
                    c.slotChoices().stream().map(slotIds::get).toList(), PassType.SINGLE);
            if (c.paid()) {
                applicationService.approvePayment(userId, a.getId());
            }
            app.put(no, a);
        }
        // 3-1 사전 관계: user05 ↔ user06 차단
        jdbc.update("insert into user_blocks (blocker_id, blocked_id, source) values (?, ?, 'REPORT')",
                app.get(5).getUserId(), app.get(6).getUserId());
        // 3-4 S3 실패 그룹 (J2용)
        for (int[] u : new int[][] {{23, 1986, 0}, {24, 1987, 1}}) {
            Long userId = rawParticipant(tag, u[0], u[1], u[2] == 1 ? "MALE" : "FEMALE");
            Application a = applicationService.submit(userId, List.of(s3.getId()), PassType.SINGLE);
            applicationService.approvePayment(userId, a.getId());
            app.put(u[0], a);
        }
    }

    @Test
    void J1_시드_매칭결과가_demo_scenario_4장과_같고_다시_실행해도_중복이_없다() {
        clock.set(s1.getMatchRunAt());
        MatchRun run = matchingService.runForSlot(s1.getId(), null);

        assertThat(run.getAlgorithmVersion()).isEqualTo("engine-v1");
        assertThat(run.getParams()).containsEntry("trigger", "SCHEDULER");
        assertThat(slotIdsOf(run)).containsExactlyInAnyOrder(s1.getId(), s2.getId());

        // S2: 1990~1993 6명이 6인 테이블로 확정, 합정 모노다이닝
        List<DiningTable> s2Tables = diningTableRepository.findByEventSlotIdOrderByIdAsc(s2.getId());
        assertThat(s2Tables).hasSize(1);
        assertThat(membersOf(s2Tables.get(0))).containsExactlyInAnyOrder(17, 18, 19, 20, 21, 22);
        assertThat(s2Tables.get(0).getVenueId()).isEqualTo(mono.getId());

        // S1: 3개 테이블, user05/06 분리, user01~13 + user15
        List<DiningTable> s1Tables = diningTableRepository.findByEventSlotIdOrderByIdAsc(s1.getId());
        assertThat(s1Tables).hasSize(3);
        Set<Integer> s1Members = s1Tables.stream().flatMap(t -> membersOf(t).stream()).collect(Collectors.toSet());
        assertThat(s1Members).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15);
        assertThat(tableOf(5)).isNotEqualTo(tableOf(6));
        assertThat(s1Tables).allSatisfy(t -> {
            assertThat(t.getTotalScore()).isNotNull();
            assertThat(t.getScoreDetail()).containsKey("items");
            assertThat(t.getSummary()).containsKeys("age", "gender", "mbti", "commonInterests");
            assertThat(t.getReservationName()).startsWith("우연한식탁-");
        });

        // user15: S2 AGE_GAP → S1 2단계 재배치, M4 → M5
        Application u15 = app.get(15);
        assertThat(u15.getMatchStatus()).isEqualTo(MatchStatus.CONFIRMED);
        assertThat(u15.getAssignedSlotId()).isEqualTo(s1.getId());
        MatchPlacementLog moved = logs(15).stream().filter(l -> l.getDecision() == MatchDecision.REALLOCATED)
                .findFirst().orElseThrow();
        assertThat(moved.getFromSlotId()).isEqualTo(s2.getId());
        assertThat(moved.getToSlotId()).isEqualTo(s1.getId());
        assertThat(moved.getReallocStep()).isEqualTo(2);
        assertThat(moved.getReasonCode()).isEqualTo("REALLOCATED_OTHER_REGION");
        assertThat(moved.getCauseCode()).isEqualTo("AGE_GAP");
        assertThat(transitionCodes(15)).endsWith("M2", "M4", "M5");
        assertThat(transitionCodes(1)).endsWith("M2", "M3");

        // user14: S1 AGE_GAP, S3 미도래 → WAITING 복귀 (M4a)
        Application u14 = app.get(14);
        assertThat(u14.getMatchStatus()).isEqualTo(MatchStatus.WAITING);
        assertThat(transitionCodes(14)).endsWith("M2", "M4a");
        assertThat(logs(14)).extracting(MatchPlacementLog::getDecision).containsExactly(MatchDecision.DEFERRED);
        assertThat(failReason(14)).isEqualTo("선택하신 다음 일정에서 다시 매칭해요");

        // user16: 미결제 → 상태 그대로, 로그만
        assertThat(app.get(16).getMatchStatus()).isNull();
        assertThat(app.get(16).getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(logs(16)).singleElement().satisfies(l -> {
            assertThat(l.getDecision()).isEqualTo(MatchDecision.EXCLUDED);
            assertThat(l.getReasonCode()).isEqualTo("NOT_PAID");
        });

        // 식당: S1 3개 중 2개 오크테이블, 1개 VENUE_UNAVAILABLE 예외 (확정은 유지)
        List<DiningTable> withVenue = s1Tables.stream().filter(DiningTable::hasVenue).toList();
        assertThat(withVenue).hasSize(2).allSatisfy(t -> assertThat(t.getVenueId()).isEqualTo(oak.getId()));
        DiningTable noVenue = s1Tables.stream().filter(t -> !t.hasVenue()).findFirst().orElseThrow();
        assertThat(capacityRepository.findByVenueIdAndEventSlotId(oak.getId(), s1.getId()).orElseThrow()
                .getReservedTables()).isEqualTo(2);
        List<ExceptionCase> venueCases = openCases(CaseType.VENUE_UNAVAILABLE);
        assertThat(venueCases).singleElement()
                .satisfies(c -> assertThat(c.getDiningTableId()).isEqualTo(noVenue.getId()));
        assertThat(run.getSummary()).containsEntry("venueAssigned", 3).containsEntry("venueUnavailable", 1);

        // E20: 같은 실행을 다시 부르면(스케줄러가 S2로, 운영자가 S1로) 기존 실행을 돌려주고 아무것도 늘지 않는다
        long tables = diningTableRepository.count();
        long logs = placementLogRepository.count();
        long cases = exceptionCaseRepository.count();
        assertThat(matchingService.runForSlot(s2.getId(), null).getId()).isEqualTo(run.getId());
        assertThat(matchingService.runForSlot(s1.getId(), operatorId).getId()).isEqualTo(run.getId());
        assertThat(diningTableRepository.count()).isEqualTo(tables);
        assertThat(placementLogRepository.count()).isEqualTo(logs);
        assertThat(exceptionCaseRepository.count()).isEqualTo(cases);

        // 운영자 재배정: 그린키친 수용량이 0이면 실패, 1로 늘리면 배정되고 예외 해결, 예약명은 처음 값 유지
        String reservationName = noVenue.getReservationName();
        assertThatThrownBy(() -> tableVenueService.assignVenue(noVenue.getId(), operatorId))
                .isInstanceOf(BusinessException.class);
        capacityRepository.findByVenueIdAndEventSlotId(green.getId(), s1.getId()).orElseThrow().changeMaxTables(1);
        DiningTable reassigned = tableVenueService.assignVenue(noVenue.getId(), operatorId);
        assertThat(reassigned.getVenueId()).isEqualTo(green.getId());
        assertThat(reassigned.getReservationName()).isEqualTo(reservationName);
        assertThat(exceptionCaseRepository.findById(venueCases.get(0).getId()).orElseThrow().getCaseStatus())
                .isEqualTo(CaseStatus.RESOLVED);
        assertThat(tableVenueService.assignVenue(noVenue.getId(), operatorId).getVenueId()).isEqualTo(green.getId());

        // 참가자 매칭 근거 (7-3): 재배치 사유는 보이고, 다른 참가자 정보·점수는 없다
        MatchQueryService.Explanation e15 = matchQueryService.explain(u15);
        assertThat(e15.criteria()).isEqualTo(MatchQueryService.CRITERIA_TEXT);
        assertThat(e15.reallocation()).isEqualTo("같은 날 선택하신 다른 지역 테이블로 조정되었어요");
        assertThat(e15.ageBand()).contains("대");
        assertThat(e15.genderRatio()).startsWith("남 ");
        assertThat(matchQueryService.explain(app.get(1)).reallocation()).isNull();
        assertThat(matchQueryService.explain(u14).message()).isEqualTo("선택하신 다음 일정에서 다시 매칭해요");
    }

    @Test
    void J2_S3_3명은_UNDER_MIN_SIZE로_대체일정_제시_1회권은_환불가능() {
        clock.set(s1.getMatchRunAt());
        matchingService.runForSlot(s1.getId(), null);

        clock.set(s3.getMatchRunAt());
        MatchRun j2 = matchingService.runForSlot(s3.getId(), null);
        assertThat(slotIdsOf(j2)).containsExactly(s3.getId());
        assertThat(diningTableRepository.findByEventSlotIdOrderByIdAsc(s3.getId())).isEmpty();

        for (int no : new int[] {14, 23, 24}) {
            Application a = app.get(no);
            assertThat(a.getMatchStatus()).as("user%02d", no).isEqualTo(MatchStatus.ALTERNATIVE_OFFERED);
            assertThat(a.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_AVAILABLE);
            assertThat(failReason(no)).isEqualTo("해당 일정에 조건이 맞는 신청자가 부족했어요");
            MatchResolution r = resolutionRepository.findByApplicationId(a.getId()).orElseThrow();
            // 성수 S4 → 강남 S5 (S1·S2는 매칭 끝, S3는 본인 선택)
            assertThat(r.getOfferedSlotIds()).containsExactly(s4.getId(), s5.getId());
        }
        assertThat(transitionCodes(23)).endsWith("M2", "M4", "M6", "P5");
        // user14의 respond_by는 원래 1순위(S1) 행사 시작 시각
        assertThat(resolutionRepository.findByApplicationId(app.get(14).getId()).orElseThrow().getRespondBy())
                .isEqualTo(s1.getStartsAt());
    }

    @Test
    void 시드_식당_수용량과_대화콘텐츠가_들어가고_콘텐츠는_한_번만_들어간다() {
        assertThat(capacityRepository.findByEventSlotIdOrderByVenueIdAsc(s4.getId())).hasSize(2)
                .allSatisfy(c -> assertThat(c.getMaxTables()).isEqualTo(2));
        assertThat(capacityRepository.findByEventSlotIdOrderByVenueIdAsc(s5.getId())).singleElement()
                .satisfies(c -> assertThat(c.getMaxTables()).isEqualTo(2));

        jdbc.update("delete from conversation_contents");
        assertThat(contentSeeder.seedIfEmpty()).isEqualTo(28);
        assertThat(contentSeeder.seedIfEmpty()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from conversation_contents where tags @> '[\"QUIET\"]'",
                Integer.class)).isEqualTo(13);
    }

    // ------------------------------------------------------------------ 도우미

    private EventSlot slot(Region region, java.time.LocalDateTime startsAt, java.time.LocalDateTime matchRunAt) {
        Instant start = startsAt.atZone(ZONE).toInstant();
        Instant run = matchRunAt.atZone(ZONE).toInstant();
        return eventSlotRepository.save(EventSlot.create(region, start, start.plus(Duration.ofHours(2)), 30, run, run));
    }

    private Long participant(String tag, int no, Candidate c) {
        Long userId = userRepository.save(User.create(String.format("user%02d-%s", no, tag), UserRole.PARTICIPANT,
                "참가자" + no, null)).getId();
        jdbc.update("""
                insert into profiles (user_id, birth_year, gender, mbti, interests, self_traits, preferred_traits,
                                      price_range)
                values (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, '20K_40K')
                """, userId, c.birthYear(), c.gender().name(), c.mbti() == null ? null : c.mbti().name(),
                json(c.interests()), json(c.selfTraits()), json(c.preferredTraits()));
        return userId;
    }

    private Long rawParticipant(String tag, int no, int birthYear, String gender) {
        Long userId = userRepository.save(User.create(String.format("user%02d-%s", no, tag), UserRole.PARTICIPANT,
                "참가자" + no, null)).getId();
        jdbc.update("""
                insert into profiles (user_id, birth_year, gender, interests, self_traits, preferred_traits)
                values (?, ?, ?, '["FOOD"]'::jsonb, '["LISTENER"]'::jsonb, '["EMPATHETIC"]'::jsonb)
                """, userId, birthYear, gender);
        return userId;
    }

    private static String json(Set<? extends Enum<?>> values) {
        return values.stream().map(v -> "\"" + v.name() + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    @SuppressWarnings("unchecked")
    private static List<Long> slotIdsOf(MatchRun run) {
        return ((List<Number>) run.getParams().get("slotIds")).stream().map(Number::longValue).toList();
    }

    private Set<Integer> membersOf(DiningTable t) {
        Map<Long, Integer> noByApp = new HashMap<>();
        app.forEach((no, a) -> noByApp.put(a.getId(), no));
        return tableMemberRepository.findByDiningTableIdAndActiveTrue(t.getId()).stream()
                .map(TableMember::getApplicationId).map(noByApp::get).collect(Collectors.toSet());
    }

    private Long tableOf(int no) {
        return tableMemberRepository.findByApplicationIdAndActiveTrue(app.get(no).getId()).orElseThrow()
                .getDiningTableId();
    }

    private List<MatchPlacementLog> logs(int no) {
        return placementLogRepository.findByApplicationIdOrderByIdAsc(app.get(no).getId());
    }

    private List<String> transitionCodes(int no) {
        return statusHistoryRepository
                .findByTargetTypeAndTargetIdOrderByIdAsc(StatusHistory.TARGET_APPLICATION, app.get(no).getId())
                .stream().map(StatusHistory::getTransitionCode).toList();
    }

    private String failReason(int no) {
        return jdbc.queryForObject("select match_fail_reason from applications where id = ?", String.class,
                app.get(no).getId());
    }

    private List<ExceptionCase> openCases(CaseType type) {
        return exceptionCaseRepository.findByCaseStatusOrderByIdAsc(CaseStatus.OPEN).stream()
                .filter(c -> c.getCaseType() == type).toList();
    }
}
