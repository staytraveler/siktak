package com.siktak;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.application.application.ApplicationService;
import com.siktak.application.domain.Application;
import com.siktak.application.domain.PassType;
import com.siktak.application.domain.status.MatchStatus;
import com.siktak.application.domain.status.PaymentStatus;
import com.siktak.payment.domain.PassStatus;
import com.siktak.payment.domain.UserPassType;
import com.siktak.payment.infra.UserPassRepository;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.domain.Region;
import com.siktak.slot.infra.EventSlotRepository;
import com.siktak.slot.infra.RegionRepository;
import com.siktak.user.domain.User;
import com.siktak.user.domain.UserRole;
import com.siktak.user.infra.UserRepository;

/** 보관 이용권(크레딧)으로 신청 (PAY-03, states.md P1a): 결제 없이 PAID → 매칭 대기, 크레딧 1장 소진. */
@Tag("db")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CreditApplicationIntegrationTest {

    @Autowired private ApplicationService applicationService;
    @Autowired private UserRepository userRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private EventSlotRepository eventSlotRepository;
    @Autowired private UserPassRepository userPassRepository;
    @Autowired private Clock clock;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void 크레딧으로_신청하면_결제_없이_매칭_대기가_되고_크레딧은_소진된다() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        Region region = regionRepository.save(Region.create("크레딧-" + run));
        Instant now = Instant.now(clock);
        Instant startsAt = now.plus(Duration.ofDays(3));
        EventSlot slot = eventSlotRepository.save(EventSlot.create(region, startsAt, startsAt.plus(Duration.ofHours(2)),
                30, now.plus(Duration.ofDays(1)), now.plus(Duration.ofDays(1))));
        User user = userRepository.save(User.create("c-" + run, UserRole.PARTICIPANT, "크레딧", null));
        jdbc.update("""
                insert into profiles (user_id, birth_year, gender, interests, self_traits, price_range,
                                      privacy_agreed_at, policy_agreed_at)
                values (?, 1996, 'FEMALE', '["FOOD"]'::jsonb, '["LISTENER"]'::jsonb, '20K_40K', now(), now())
                """, user.getId());

        EventSlot slot2 = eventSlotRepository.save(EventSlot.create(region, startsAt.plus(Duration.ofDays(1)),
                startsAt.plus(Duration.ofDays(1)).plus(Duration.ofHours(2)), 30, now.plus(Duration.ofDays(1)),
                now.plus(Duration.ofDays(1))));
        Application source = applicationService.submit(user.getId(), List.of(slot.getId()), PassType.SINGLE);
        userPassRepository.save(com.siktak.payment.domain.UserPass.singleCredit(user.getId(), source.getId(), now));

        Application application = applicationService.submit(user.getId(), List.of(slot2.getId()), PassType.CREDIT);
        assertThat(application.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(application.getMatchStatus()).isEqualTo(MatchStatus.WAITING);
        assertThat(application.getPaymentId()).isNull();
        assertThat(userPassRepository.findByUserIdAndPassTypeAndPassStatus(user.getId(), UserPassType.SINGLE_CREDIT,
                PassStatus.ACTIVE)).isEmpty();
    }
}
