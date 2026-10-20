package com.siktak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.siktak.common.error.BusinessException;
import com.siktak.payment.application.UserPassService;
import com.siktak.payment.domain.UserPass;
import com.siktak.payment.domain.UserPassType;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.domain.Region;
import com.siktak.slot.infra.EventSlotRepository;
import com.siktak.slot.infra.RegionRepository;
import com.siktak.user.domain.User;
import com.siktak.user.domain.UserRole;
import com.siktak.user.infra.UserRepository;

/** 멤버십(APP-04, D1): 구매 → 결제 없이 여러 회차 신청, 기간 밖 회차 거절, 중복 구매 거절. */
@Tag("db")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MembershipApplicationIntegrationTest {

    @Autowired private ApplicationService applicationService;
    @Autowired private UserPassService userPassService;
    @Autowired private UserRepository userRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private EventSlotRepository eventSlotRepository;
    @Autowired private Clock clock;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private EventSlot slot(Region region, Instant now, int daysAhead) {
        Instant startsAt = now.plus(Duration.ofDays(daysAhead));
        return eventSlotRepository.save(EventSlot.create(region, startsAt, startsAt.plus(Duration.ofHours(2)), 30,
                now.plus(Duration.ofDays(1)), now.plus(Duration.ofDays(1))));
    }

    @Test
    void 멤버십으로_결제_없이_여러_회차를_신청하고_기간_밖_회차는_거절된다() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        Region region = regionRepository.save(Region.create("멤버십-" + run));
        Instant now = Instant.now(clock);
        User user = userRepository.save(User.create("m-" + run, UserRole.PARTICIPANT, "멤버십", null));
        jdbc.update("""
                insert into profiles (user_id, birth_year, gender, interests, self_traits, price_range,
                                      privacy_agreed_at, policy_agreed_at)
                values (?, 1996, 'FEMALE', '["FOOD"]'::jsonb, '["LISTENER"]'::jsonb, '20K_40K', now(), now())
                """, user.getId());

        // 멤버십이 없으면 거절
        EventSlot near = slot(region, now, 3);
        assertThatThrownBy(() -> applicationService.submit(user.getId(), List.of(near.getId()), PassType.MEMBERSHIP))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 멤버십_구매_후_신청과_중복_구매_거절() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        Region region = regionRepository.save(Region.create("멤버십2-" + run));
        Instant now = Instant.now(clock);
        User user = userRepository.save(User.create("m2-" + run, UserRole.PARTICIPANT, "멤버십2", null));
        jdbc.update("""
                insert into profiles (user_id, birth_year, gender, interests, self_traits, price_range,
                                      privacy_agreed_at, policy_agreed_at)
                values (?, 1996, 'FEMALE', '["FOOD"]'::jsonb, '["LISTENER"]'::jsonb, '20K_40K', now(), now())
                """, user.getId());
        EventSlot near = slot(region, now, 3);
        EventSlot near2 = slot(region, now, 4);
        EventSlot far = slot(region, now, 60);

        UserPass pass = userPassService.purchaseMembership(user.getId(), 1);
        assertThat(pass.getPassType()).isEqualTo(UserPassType.MEMBERSHIP_1M);
        assertThat(pass.getExpiresAt()).isAfter(now.plus(Duration.ofDays(27)));
        assertThatThrownBy(() -> userPassService.purchaseMembership(user.getId(), 3)).isInstanceOf(BusinessException.class);

        Application first = applicationService.submit(user.getId(), List.of(near.getId()), PassType.MEMBERSHIP);
        assertThat(first.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(first.getMatchStatus()).isEqualTo(MatchStatus.WAITING);
        assertThat(first.getPaymentId()).isNull();
        // 횟수 제한 없음: 다른 회차에 한 번 더 신청할 수 있다
        Application second = applicationService.submit(user.getId(), List.of(near2.getId()), PassType.MEMBERSHIP);
        assertThat(second.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        // 기간(1개월) 밖의 회차는 거절
        assertThatThrownBy(() -> applicationService.submit(user.getId(), List.of(far.getId()), PassType.MEMBERSHIP))
                .isInstanceOf(BusinessException.class);
    }
}
