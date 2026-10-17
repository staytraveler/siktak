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
 * demo-scenario.md 2·3장 시드(J1·J2 대상)를 실제 DB에 넣는 DB 통합 테스트 공통 기반 (4.4·6.1).
 * 시각은 데모 도구처럼 테스트 시계를 옮겨서 진행하고, 트랜잭션 롤백으로 데이터를 남기지 않는다.
 */
@Tag("db")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest
@ActiveProfiles("test")
@Import(DemoScenarioDbTestSupport.MovableClockConfig.class)
@Transactional
abstract class DemoScenarioDbTestSupport {

    protected static final ZoneId ZONE = ClockConfig.ZONE;

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

    @Autowired protected MovableClock clock;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UserRepository userRepository;
    @Autowired protected RegionRepository regionRepository;
    @Autowired protected EventSlotRepository eventSlotRepository;
    @Autowired protected ApplicationService applicationService;
    @Autowired protected MatchingService matchingService;
    @Autowired protected TableVenueService tableVenueService;
    @Autowired protected MatchQueryService matchQueryService;
    @Autowired protected DemoVenueSeed demoVenueSeed;
    @Autowired protected ConversationContentSeeder contentSeeder;
    @Autowired protected VenueSlotCapacityRepository capacityRepository;
    @Autowired protected DiningTableRepository diningTableRepository;
    @Autowired protected TableMemberRepository tableMemberRepository;
    @Autowired protected MatchPlacementLogRepository placementLogRepository;
    @Autowired protected MatchResolutionRepository resolutionRepository;
    @Autowired protected ExceptionCaseRepository exceptionCaseRepository;
    @Autowired protected StatusHistoryRepository statusHistoryRepository;

    protected EventSlot s1;
    protected EventSlot s2;
    protected EventSlot s3;
    protected EventSlot s4;
    protected EventSlot s5;
    protected Venue oak;
    protected Venue green;
    protected Venue mono;
    protected Long operatorId;
    /** userNN 번호 → 신청 */
    protected final Map<Integer, Application> app = new HashMap<>();

    /** demo-scenario.md 2·3장: T0 기준 D = T0 + 3일, S1·S2 매칭 = D−2일 12:00, S3 = D−1일 12:00, S4·S5 = D+5일 12:00. */
    @BeforeEach
    void seed() {
        // 공유 DB에 이미 있는 모집 중 회차(데모 시드 등)가 도래 회차 일괄 실행·대체 일정 추천에 섞이지 않게 빼 둔다 (롤백됨)
        jdbc.update("update event_slots set slot_status = 'MATCHED' where slot_status in ('OPEN', 'CLOSED')");
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

    // ------------------------------------------------------------------ 도우미

    protected EventSlot slot(Region region, java.time.LocalDateTime startsAt, java.time.LocalDateTime matchRunAt) {
        Instant start = startsAt.atZone(ZONE).toInstant();
        Instant run = matchRunAt.atZone(ZONE).toInstant();
        return eventSlotRepository.save(EventSlot.create(region, start, start.plus(Duration.ofHours(2)), 30, run, run));
    }

    protected Long participant(String tag, int no, Candidate c) {
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

    protected Long rawParticipant(String tag, int no, int birthYear, String gender) {
        Long userId = userRepository.save(User.create(String.format("user%02d-%s", no, tag), UserRole.PARTICIPANT,
                "참가자" + no, null)).getId();
        jdbc.update("""
                insert into profiles (user_id, birth_year, gender, interests, self_traits, preferred_traits)
                values (?, ?, ?, '["FOOD"]'::jsonb, '["LISTENER"]'::jsonb, '["EMPATHETIC"]'::jsonb)
                """, userId, birthYear, gender);
        return userId;
    }

    protected static String json(Set<? extends Enum<?>> values) {
        return values.stream().map(v -> "\"" + v.name() + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    @SuppressWarnings("unchecked")
    protected static List<Long> slotIdsOf(MatchRun run) {
        return ((List<Number>) run.getParams().get("slotIds")).stream().map(Number::longValue).toList();
    }

    protected Set<Integer> membersOf(DiningTable t) {
        Map<Long, Integer> noByApp = new HashMap<>();
        app.forEach((no, a) -> noByApp.put(a.getId(), no));
        return tableMemberRepository.findByDiningTableIdAndActiveTrue(t.getId()).stream()
                .map(TableMember::getApplicationId).map(noByApp::get).collect(Collectors.toSet());
    }

    protected Long tableOf(int no) {
        return tableMemberRepository.findByApplicationIdAndActiveTrue(app.get(no).getId()).orElseThrow()
                .getDiningTableId();
    }

    protected List<MatchPlacementLog> logs(int no) {
        return placementLogRepository.findByApplicationIdOrderByIdAsc(app.get(no).getId());
    }

    protected List<String> transitionCodes(int no) {
        return statusHistoryRepository
                .findByTargetTypeAndTargetIdOrderByIdAsc(StatusHistory.TARGET_APPLICATION, app.get(no).getId())
                .stream().map(StatusHistory::getTransitionCode).toList();
    }

    protected String failReason(int no) {
        return jdbc.queryForObject("select match_fail_reason from applications where id = ?", String.class,
                app.get(no).getId());
    }

    protected List<ExceptionCase> openCases(CaseType type) {
        return exceptionCaseRepository.findByCaseStatusOrderByIdAsc(CaseStatus.OPEN).stream()
                .filter(c -> c.getCaseType() == type).toList();
    }
}
