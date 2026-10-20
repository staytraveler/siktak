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
import com.siktak.exception.domain.CaseStatus;
import com.siktak.exception.domain.CaseType;
import com.siktak.exception.infra.ExceptionCaseRepository;
import com.siktak.payment.infra.MockPaymentGateway;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.domain.Region;
import com.siktak.slot.infra.EventSlotRepository;
import com.siktak.slot.infra.RegionRepository;
import com.siktak.user.domain.User;
import com.siktak.user.domain.UserRole;
import com.siktak.user.infra.UserRepository;

/** 결제 승인 실패 → FAILED + 결제 오류 예외, 재결제 성공 → PAID + 예외 자동 해소 (MAT-13, states.md P3·P4). */
@Tag("db")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentErrorIntegrationTest {

    @Autowired private ApplicationService applicationService;
    @Autowired private UserRepository userRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private EventSlotRepository eventSlotRepository;
    @Autowired private ExceptionCaseRepository exceptionCaseRepository;
    @Autowired private MockPaymentGateway mockGateway;
    @Autowired private com.siktak.notification.infra.NotificationRepository notificationRepository;
    @Autowired private Clock clock;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void 결제가_실패하면_예외가_올라가고_재결제하면_해소된다() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        Region region = regionRepository.save(Region.create("결제오류-" + run));
        Instant now = Instant.now(clock);
        Instant startsAt = now.plus(Duration.ofDays(3));
        EventSlot slot = eventSlotRepository.save(EventSlot.create(region, startsAt, startsAt.plus(Duration.ofHours(2)),
                30, now.plus(Duration.ofDays(1)), now.plus(Duration.ofDays(1))));
        User user = userRepository.save(User.create("pe-" + run, UserRole.PARTICIPANT, "결제오류", null));
        jdbc.update("""
                insert into profiles (user_id, birth_year, gender, interests, self_traits, price_range,
                                      privacy_agreed_at, policy_agreed_at)
                values (?, 1996, 'FEMALE', '["FOOD"]'::jsonb, '["LISTENER"]'::jsonb, '20K_40K', now(), now())
                """, user.getId());
        Application application = applicationService.submit(user.getId(), List.of(slot.getId()), PassType.SINGLE);
        String caseKey = "PAYMENT_ERROR:application-" + application.getId();

        // INF-01·07: 인앱 알림과 함께 푸시 발송 결과가 기록된다
        var sent = notificationRepository.findByUserIdAndSendStatusOrderByIdDesc(user.getId(),
                com.siktak.notification.domain.NotificationStatus.SENT);
        assertThat(sent).isNotEmpty().allSatisfy(n -> assertThat(n.getPushStatus()).isEqualTo("SENT"));

        mockGateway.setFailApprove(true);
        try {
            applicationService.approvePayment(user.getId(), application.getId());
            assertThat(application.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
            var opened = exceptionCaseRepository.findByCaseKey(caseKey).orElseThrow();
            assertThat(opened.getCaseType()).isEqualTo(CaseType.PAYMENT_ERROR);
            assertThat(opened.getCaseStatus()).isNotEqualTo(CaseStatus.RESOLVED);
        } finally {
            mockGateway.setFailApprove(false);
        }

        applicationService.approvePayment(user.getId(), application.getId());
        assertThat(application.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(application.getMatchStatus()).isEqualTo(MatchStatus.WAITING);
        assertThat(exceptionCaseRepository.findByCaseKey(caseKey).orElseThrow().getCaseStatus())
                .isEqualTo(CaseStatus.RESOLVED);
    }
}
