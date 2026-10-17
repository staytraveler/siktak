package com.siktak.common.seed;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.application.application.ApplicationService;
import com.siktak.application.domain.Application;
import com.siktak.application.domain.PassType;
import com.siktak.common.config.ClockConfig;
import com.siktak.common.demo.DemoClockService;
import com.siktak.common.settings.Setting;
import com.siktak.common.settings.SettingRepository;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.domain.Region;
import com.siktak.slot.infra.EventSlotRepository;
import com.siktak.slot.infra.RegionRepository;
import com.siktak.user.domain.User;
import com.siktak.user.domain.UserRole;
import com.siktak.user.infra.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * demo-v1 시드 초기화 (docs/demo-scenario.md). 한 트랜잭션에서:
 * 데이터 테이블 비우기(flyway_schema_history·shedlock 제외) → 데모 시계 offset 0 → 기본 시드 → 확장 시드({@link SeedContributor}).
 * 작업 실행기 재적재는 커밋 후 호출 측(DemoService)이 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoSeedService {

    public static final String SEED_KEY = "demo.seed";
    private static final List<String> KEEP_TABLES = List.of("flyway_schema_history", "shedlock");

    private final JdbcTemplate jdbcTemplate;
    private final DemoClockService demoClockService;
    private final SettingRepository settingRepository;
    private final RegionRepository regionRepository;
    private final EventSlotRepository eventSlotRepository;
    private final UserRepository userRepository;
    private final ApplicationService applicationService;
    private final PasswordEncoder passwordEncoder;
    private final List<SeedContributor> contributors;
    private final Clock clock;

    @Transactional
    public SeedContext reset(Long operatorId) {
        truncateDataTables();
        demoClockService.reset(operatorId);
        Instant t0 = Instant.now(clock);

        Map<String, Long> regionIds = new LinkedHashMap<>();
        for (String name : DemoSeedData.REGIONS) {
            regionIds.put(name, regionRepository.save(Region.create(name)).getId());
        }
        Map<String, Long> slotIds = seedSlots(t0);
        Map<String, Long> userIds = seedUsers(t0);
        seedBlocks(userIds);
        seedApplications(slotIds, userIds);

        SeedContext context = new SeedContext(t0, Map.copyOf(slotIds), Map.copyOf(userIds), Map.copyOf(regionIds));
        contributors.stream().sorted(Comparator.comparingInt(SeedContributor::order))
                .forEach(contributor -> contributor.contribute(context));

        Map<String, Object> seedInfo = new LinkedHashMap<>();
        seedInfo.put("version", DemoSeedData.VERSION);
        seedInfo.put("t0", t0.toString());
        seedInfo.put("slots", slotIds);
        settingRepository.save(Setting.of(SEED_KEY, seedInfo, operatorId));
        log.info("Demo seed {} loaded at {}: users={}, slots={}", DemoSeedData.VERSION, t0, userIds.size(), slotIds);
        return context;
    }

    private void truncateDataTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = current_schema()", String.class).stream()
                .filter(t -> !KEEP_TABLES.contains(t))
                .toList();
        if (!tables.isEmpty()) {
            jdbcTemplate.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
        }
    }

    /**
     * 회차 (2장). D = T0 + 3일. 정원 30, 2시간, 신청 마감 = 매칭 실행 시각.
     * S1·S2: D 19:30 / 매칭 D−2 12:00, S3: D+1 19:30 / D−1 12:00, S4·S5: D+7 19:30 / D+5 12:00
     */
    private Map<String, Long> seedSlots(Instant t0) {
        LocalDate d = t0.atZone(ClockConfig.ZONE).toLocalDate().plusDays(3);
        Map<String, Long> ids = new LinkedHashMap<>();
        ids.put("S1", slot("성수", d, 0, -2));
        ids.put("S2", slot("홍대/합정", d, 0, -2));
        ids.put("S3", slot("성수", d, 1, -1));
        ids.put("S4", slot("성수", d, 7, 5));
        ids.put("S5", slot("강남", d, 7, 5));
        return ids;
    }

    private Long slot(String regionName, LocalDate d, int eventDayOffset, int matchDayOffset) {
        Region region = regionRepository.findByName(regionName).orElseThrow();
        Instant startsAt = d.plusDays(eventDayOffset).atTime(LocalTime.of(19, 30)).atZone(ClockConfig.ZONE).toInstant();
        Instant matchRunAt = d.plusDays(matchDayOffset).atTime(LocalTime.NOON).atZone(ClockConfig.ZONE).toInstant();
        return eventSlotRepository.save(EventSlot.create(region, startsAt, startsAt.plus(Duration.ofHours(2)), 30,
                matchRunAt, matchRunAt)).getId();
    }

    private Map<String, Long> seedUsers(Instant t0) {
        Map<String, Long> ids = new LinkedHashMap<>();
        ids.put(DemoSeedData.ADMIN_LOGIN, userRepository.save(User.create(DemoSeedData.ADMIN_LOGIN, UserRole.OPERATOR,
                "운영자", null, passwordEncoder.encode(DemoSeedData.ADMIN_PASSWORD))).getId());
        // 테스트 계정은 모두 같은 비밀번호라 해시 1회를 재사용한다 (BCrypt 비용 절감)
        String userHash = passwordEncoder.encode(DemoSeedData.USER_PASSWORD);
        Timestamp agreedAt = Timestamp.from(t0);
        List<DemoSeedData.Person> people = DemoSeedData.PEOPLE;
        for (int i = 0; i < people.size(); i++) {
            DemoSeedData.Person p = people.get(i);
            Long id = userRepository.save(User.create(p.loginId(), UserRole.PARTICIPANT, p.name(),
                    String.format("010-1000-%04d", i + 1), userHash)).getId();
            ids.put(p.loginId(), id);
            jdbcTemplate.update("""
                    INSERT INTO profiles (user_id, birth_year, gender, job, mbti, interests, self_traits, preferred_traits,
                                          conversation_style, price_range, privacy_agreed_at, policy_agreed_at)
                    VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
                    """, id, p.birthYear(), p.gender().equals("F") ? "FEMALE" : "MALE",
                    DemoSeedData.JOBS.get(i % DemoSeedData.JOBS.size()), p.mbti(),
                    json(p.interests()), json(p.selfTraits()), json(p.preferredTraits()),
                    DemoSeedData.CONVERSATION_STYLES.get(i % DemoSeedData.CONVERSATION_STYLES.size()),
                    DemoSeedData.PRICE_RANGES.get(i % DemoSeedData.PRICE_RANGES.size()), agreedAt, agreedAt);
        }
        return ids;
    }

    /** 차단 관계. user_blocks는 V2의 V3 마이그레이션에서 생긴다 — 테이블이 있을 때만 넣는다. */
    private void seedBlocks(Map<String, Long> userIds) {
        Boolean exists = jdbcTemplate.queryForObject("SELECT to_regclass('user_blocks') IS NOT NULL", Boolean.class);
        if (!Boolean.TRUE.equals(exists)) {
            log.warn("user_blocks table not found; demo block relation (user05-user06) skipped");
            return;
        }
        for (String[] pair : DemoSeedData.BLOCKS) {
            jdbcTemplate.update("INSERT INTO user_blocks (blocker_id, blocked_id, source) VALUES (?, ?, 'REPORT')",
                    userIds.get(pair[0]), userIds.get(pair[1]));
        }
    }

    /** 신청·결제는 실제 서비스 흐름으로 만든다 → 상태 이력(A1·A2·P1·P2·M1)도 함께 남는다. */
    private void seedApplications(Map<String, Long> slotIds, Map<String, Long> userIds) {
        for (DemoSeedData.Person p : DemoSeedData.PEOPLE) {
            if (p.slots().isEmpty()) {
                continue;
            }
            Long userId = userIds.get(p.loginId());
            Application application = applicationService.submit(userId,
                    p.slots().stream().map(slotIds::get).toList(), PassType.SINGLE);
            if (p.paid()) {
                applicationService.approvePayment(userId, application.getId());
            }
        }
    }

    private static String json(List<String> values) {
        return values.stream().map(v -> "\"" + v + "\"").reduce((a, b) -> a + "," + b)
                .map(s -> "[" + s + "]").orElse("[]");
    }
}
