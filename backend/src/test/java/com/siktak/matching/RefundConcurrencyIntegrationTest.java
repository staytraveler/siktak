package com.siktak.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.siktak.application.application.ApplicationService;
import com.siktak.application.application.RefundService;
import com.siktak.application.application.StatusTransitionService;
import com.siktak.application.domain.Application;
import com.siktak.application.domain.PassType;
import com.siktak.application.domain.status.ActorType;
import com.siktak.application.domain.status.Transition;
import com.siktak.application.domain.status.TransitionContext;
import com.siktak.application.infra.ApplicationRepository;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.matching.application.ResolutionService;
import com.siktak.matching.domain.MatchResolution;
import com.siktak.matching.domain.ResolutionChoice;
import com.siktak.matching.infra.MatchResolutionRepository;
import com.siktak.payment.infra.MockPaymentGateway;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.domain.Region;
import com.siktak.slot.infra.EventSlotRepository;
import com.siktak.slot.infra.RegionRepository;
import com.siktak.user.domain.User;
import com.siktak.user.domain.UserRole;
import com.siktak.user.infra.UserRepository;

/**
 * 중복 환불 방지 (6.1): 같은 신청에 환불을 동시에 두 번 요청해도 PG 취소는 한 번만 불린다.
 * 동시 요청은 각자 커밋해야 하므로 롤백 테스트가 아니다. 만든 데이터는 끝나고 지운다.
 */
@Tag("db")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest
@ActiveProfiles("test")
class RefundConcurrencyIntegrationTest {

    @Autowired private Clock clock;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository userRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private EventSlotRepository eventSlotRepository;
    @Autowired private ApplicationService applicationService;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private StatusTransitionService transitionService;
    @Autowired private MatchResolutionRepository resolutionRepository;
    @Autowired private ResolutionService resolutionService;
    @Autowired private RefundService refundService;
    @Autowired private MockPaymentGateway mockGateway;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private final List<Long> userIds = new ArrayList<>();
    private Long regionId;
    private Long slotId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Instant now = clock.instant();
        tx.executeWithoutResult(s -> {
            Region region = regionRepository.save(Region.create("동시성-" + tag));
            Instant start = now.plus(Duration.ofDays(30));
            EventSlot slot = eventSlotRepository.save(EventSlot.create(region, start, start.plus(Duration.ofHours(2)),
                    30, start.minus(Duration.ofDays(2)), start.minus(Duration.ofDays(2))));
            regionId = region.getId();
            slotId = slot.getId();
        });
    }

    @AfterEach
    void cleanUp() {
        mockGateway.setFailCancel(false);
        for (Long userId : userIds) {
            List<Long> appIds = jdbc.queryForList("select id from applications where user_id = ?", Long.class, userId);
            for (Long appId : appIds) {
                jdbc.update("delete from status_histories where target_type = 'APPLICATION' and target_id = ?", appId);
                jdbc.update("delete from exception_cases where application_id = ?", appId);
                jdbc.update("delete from match_resolutions where application_id = ?", appId);
            }
            jdbc.update("delete from user_passes where user_id = ?", userId);
            jdbc.update("delete from notifications where user_id = ?", userId);
            for (Long appId : appIds) {
                jdbc.update("delete from application_slot_choices where application_id = ?", appId);
            }
            jdbc.update("delete from applications where user_id = ?", userId);
            jdbc.update("delete from payments where user_id = ?", userId);
            jdbc.update("delete from profiles where user_id = ?", userId);
            jdbc.update("delete from users where id = ?", userId);
        }
        jdbc.update("delete from event_slots where id = ?", slotId);
        jdbc.update("delete from regions where id = ?", regionId);
    }

    @Test
    void 환불_선택을_동시에_두_번_보내도_PG_취소는_1번이다() throws Exception {
        Application a = failedApplication();

        List<Throwable> errors = concurrently(2,
                () -> resolutionService.choose(a.getUserId(), a.getId(), ResolutionChoice.REFUND, null));

        assertThat(paymentStatus(a.getId())).isEqualTo("REFUNDED");
        assertThat(mockGateway.cancelCallCount(refundKey(a.getId()))).isEqualTo(1);
        // 늦은 요청은 같은 선택이라 무시되거나(멱등), 먼저 커밋된 요청과 부딪혀 409
        assertThat(errors).allSatisfy(e -> assertThat(e).isInstanceOfSatisfying(BusinessException.class,
                b -> assertThat(b.getErrorCode()).isEqualTo(ErrorCode.CONFLICT)));
    }

    @Test
    void 환불_처리_중인_신청을_동시에_재시도해도_PG_취소_성공은_1번이다() throws Exception {
        Application a = failedApplication();
        mockGateway.setFailCancel(true);
        resolutionService.choose(a.getUserId(), a.getId(), ResolutionChoice.REFUND, null);
        assertThat(paymentStatus(a.getId())).isEqualTo("REFUND_PROCESSING");
        mockGateway.setFailCancel(false);

        concurrently(2, () -> refundService.process(a.getId()));

        assertThat(paymentStatus(a.getId())).isEqualTo("REFUNDED");
        // 첫 실패 1번 + 성공 1번. 늦은 재시도는 결제 행 잠금 뒤 이미 취소된 거래를 보고 PG를 부르지 않는다
        assertThat(mockGateway.cancelCallCount(refundKey(a.getId()))).isEqualTo(2);
    }

    /** 1회권 결제 후 매칭 실패(M2 → M4 → M6 + P5)로 대체 일정 선택 대기인 신청. */
    private Application failedApplication() {
        return tx.execute(s -> {
            String tag = UUID.randomUUID().toString().substring(0, 8);
            User user = userRepository.save(User.create("conc-" + tag, UserRole.PARTICIPANT, "동시성", null));
            jdbc.update("""
                    insert into profiles (user_id, birth_year, gender, interests, self_traits, price_range,
                                          privacy_agreed_at, policy_agreed_at)
                    values (?, 1996, 'FEMALE', '["FOOD"]'::jsonb, '["LISTENER"]'::jsonb, '20K_40K', now(), now())
                    """, user.getId());
            userIds.add(user.getId());
            Application a = applicationService.submit(user.getId(), List.of(slotId), PassType.SINGLE);
            applicationService.approvePayment(user.getId(), a.getId());
            transitionService.apply(a, Transition.M2, TransitionContext.of(ActorType.SCHEDULER, null, "테스트"));
            transitionService.apply(a, Transition.M4, TransitionContext.system("테스트"));
            transitionService.apply(a, Transition.M6, TransitionContext.system("테스트"));
            transitionService.apply(a, Transition.P5, TransitionContext.system("테스트"));
            resolutionRepository.save(MatchResolution.offer(a.getId(), List.of(),
                    clock.instant().plus(Duration.ofDays(30))));
            return applicationRepository.save(a);
        });
    }

    private List<Throwable> concurrently(int n, Callable<?> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        ready.await();
        go.countDown();
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        for (Future<?> f : futures) {
            try {
                f.get(30, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException e) {
                errors.add(e.getCause());
            }
        }
        pool.shutdown();
        return errors;
    }

    private String paymentStatus(Long applicationId) {
        return jdbc.queryForObject("select payment_status from applications where id = ?", String.class,
                applicationId);
    }

    private String refundKey(Long applicationId) {
        return jdbc.queryForObject(
                "select p.refund_key from payments p join applications a on a.payment_id = p.id where a.id = ?",
                String.class, applicationId);
    }
}
