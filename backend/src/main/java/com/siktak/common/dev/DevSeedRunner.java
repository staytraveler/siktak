package com.siktak.common.dev;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.common.config.ClockConfig;
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
 * [개발용 시드 — 2.3 워킹 스켈레톤] 지역·테스트 사용자·회차 1개를 없을 때만 넣는다 (멱등).
 * 정식 시드(docs/demo-scenario.md)와 데모 도구는 2.4에서 이 클래스를 대체한다.
 * siktak.dev-seed.enabled=false로 끌 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "siktak.dev-seed.enabled", havingValue = "true")
public class DevSeedRunner implements ApplicationRunner {

    private static final List<String> REGIONS = List.of("성수", "홍대/합정", "강남");

    /** loginId, 이름, 출생연도, 성별, MBTI */
    private static final List<Object[]> PARTICIPANTS = List.of(
            new Object[] {"user01", "김하늘", 1996, "FEMALE", "ENFP"},
            new Object[] {"user02", "이준호", 1995, "MALE", "ISTJ"},
            new Object[] {"user03", "박서연", 1997, "FEMALE", "INFJ"},
            new Object[] {"user04", "최민수", 1994, "MALE", "ENTP"},
            new Object[] {"user05", "정유나", 1998, "FEMALE", "ESFJ"},
            new Object[] {"user06", "한도윤", 1996, "MALE", "INTP"});

    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final EventSlotRepository eventSlotRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        REGIONS.forEach(name -> regionRepository.findByName(name)
                .orElseGet(() -> regionRepository.save(Region.create(name))));

        if (userRepository.findByLoginId("admin01").isEmpty()) {
            userRepository.save(User.create("admin01", UserRole.OPERATOR, "운영자", null));
        }
        Instant now = Instant.now(clock);
        for (Object[] p : PARTICIPANTS) {
            String loginId = (String) p[0];
            User user = userRepository.findByLoginId(loginId)
                    .orElseGet(() -> userRepository.save(User.create(loginId, UserRole.PARTICIPANT, (String) p[1],
                            "010-0000-" + loginId.substring(4).repeat(2))));
            jdbcTemplate.update("""
                    INSERT INTO profiles (user_id, birth_year, gender, mbti, interests, self_traits, preferred_traits,
                                          conversation_style, privacy_agreed_at, policy_agreed_at)
                    VALUES (?, ?, ?, ?, '["FOOD","TRAVEL"]'::jsonb, '["EMPATHETIC"]'::jsonb, '["CURIOUS"]'::jsonb,
                            'BALANCED', ?, ?)
                    ON CONFLICT (user_id) DO NOTHING
                    """, user.getId(), p[2], p[3], p[4], java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        }

        if (eventSlotRepository.count() == 0) {
            Region region = regionRepository.findByName("성수").orElseThrow();
            LocalDate day = now.atZone(ClockConfig.ZONE).toLocalDate().plusDays(3);
            Instant startsAt = day.atTime(LocalTime.of(19, 30)).atZone(ClockConfig.ZONE).toInstant();
            Instant matchRunAt = startsAt.minus(Duration.ofDays(2));
            eventSlotRepository.save(EventSlot.create(region, startsAt, startsAt.plus(Duration.ofHours(2)), 30,
                    matchRunAt, matchRunAt));
        }
        log.info("Dev seed ready: regions={}, users={}, slots={}",
                regionRepository.count(), userRepository.count(), eventSlotRepository.count());
    }
}
