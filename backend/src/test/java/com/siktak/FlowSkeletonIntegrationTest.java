package com.siktak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
import com.siktak.application.domain.status.ApplicationStatus;
import com.siktak.application.domain.status.MatchStatus;
import com.siktak.application.domain.status.PaymentStatus;
import com.siktak.application.domain.status.StatusHistory;
import com.siktak.application.infra.StatusHistoryRepository;
import com.siktak.chat.application.ChatService;
import com.siktak.chat.domain.ChatMessage;
import com.siktak.common.error.BusinessException;
import com.siktak.feedback.application.FeedbackService;
import com.siktak.matching.application.MatchingService;
import com.siktak.matching.domain.DiningTable;
import com.siktak.matching.domain.MatchRun;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.domain.Region;
import com.siktak.slot.infra.EventSlotRepository;
import com.siktak.slot.infra.RegionRepository;
import com.siktak.user.domain.User;
import com.siktak.user.domain.UserRole;
import com.siktak.user.infra.UserRepository;

/**
 * 2.3 워킹 스켈레톤 흐름: 신청 → Mock 결제 → 단순 매칭 → 확정 → 단체방 메시지 → 피드백.
 * 실제 PostgreSQL(Flyway V2)에서 돌리고 트랜잭션 롤백으로 데이터를 남기지 않는다.
 */
@Tag("db")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FlowSkeletonIntegrationTest {

    @Autowired private ApplicationService applicationService;
    @Autowired private MatchingService matchingService;
    @Autowired private ChatService chatService;
    @Autowired private FeedbackService feedbackService;
    @Autowired private UserRepository userRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private EventSlotRepository eventSlotRepository;
    @Autowired private StatusHistoryRepository statusHistoryRepository;
    @Autowired private Clock clock;

    @Test
    void 신청부터_피드백까지_한_번에_이어지고_매칭은_다시_실행해도_중복되지_않는다() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        Region region = regionRepository.save(Region.create("테스트-" + run));
        Instant now = Instant.now(clock);
        Instant startsAt = now.plus(Duration.ofDays(3));
        EventSlot slot = eventSlotRepository.save(EventSlot.create(region, startsAt, startsAt.plus(Duration.ofHours(2)),
                30, now.plus(Duration.ofDays(1)), now.plus(Duration.ofDays(1))));
        User operator = userRepository.save(User.create("op-" + run, UserRole.OPERATOR, "운영자", null));

        // 신청 7명 → 결제 → 테이블 1개(6명) + 미배치 1명
        List<Application> applications = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            User user = userRepository.save(User.create("u" + i + "-" + run, UserRole.PARTICIPANT, "참가자" + i, null));
            Application application = applicationService.submit(user.getId(), List.of(slot.getId()), PassType.SINGLE);
            assertThat(application.getApplicationStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
            assertThat(application.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(application.getMatchStatus()).isNull();
            applicationService.approvePayment(user.getId(), application.getId());
            // 결제 승인은 멱등
            applicationService.approvePayment(user.getId(), application.getId());
            assertThat(application.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(application.getMatchStatus()).isEqualTo(MatchStatus.WAITING);
            applications.add(application);
        }

        // 같은 회차 중복 신청은 거절
        Long firstUser = applications.get(0).getUserId();
        assertThatThrownBy(() -> applicationService.submit(firstUser, List.of(slot.getId()), PassType.SINGLE))
                .isInstanceOf(BusinessException.class);

        MatchRun first = matchingService.runForSlot(slot.getId(), operator.getId());
        MatchRun second = matchingService.runForSlot(slot.getId(), operator.getId());
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(first.getSummary()).containsEntry("tables", 1).containsEntry("unplaced", 1);

        List<DiningTable> tables = matchingService.tablesOf(slot.getId());
        assertThat(tables).hasSize(1);
        DiningTable table = tables.get(0);
        assertThat(table.getMemberCount()).isEqualTo(6);

        Application confirmed = applications.get(0);
        assertThat(confirmed.getMatchStatus()).isEqualTo(MatchStatus.CONFIRMED);
        assertThat(confirmed.getAssignedSlotId()).isEqualTo(slot.getId());
        assertThat(applications.get(6).getMatchStatus()).isEqualTo(MatchStatus.WAITING);

        // 단체방: 멤버는 확정 메시지를 보고, 멤버가 아니면 볼 수 없다
        Long roomId = chatService.findByTable(table.getId()).orElseThrow().getId();
        List<ChatMessage> messages = chatService.messagesFor(roomId, confirmed.getUserId());
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getSenderType()).isEqualTo(ChatMessage.SenderType.SYSTEM);
        Long outsider = applications.get(6).getUserId();
        assertThatThrownBy(() -> chatService.messagesFor(roomId, outsider)).isInstanceOf(BusinessException.class);

        // 피드백: 확정자만, 한 번만
        feedbackService.submit(confirmed.getUserId(), confirmed.getId(), 5, 4, 3, "좋았어요");
        assertThatThrownBy(() -> feedbackService.submit(confirmed.getUserId(), confirmed.getId(), 5, 5, 5, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> feedbackService.submit(outsider, applications.get(6).getId(), 5, 5, 5, null))
                .isInstanceOf(BusinessException.class);

        // 감사 로그: A1 → A2 → P1 → P2 → M1 → (no-op 결제 없음) → M2 → M3
        List<String> codes = statusHistoryRepository
                .findByTargetTypeAndTargetIdOrderByIdAsc(StatusHistory.TARGET_APPLICATION, confirmed.getId())
                .stream().map(StatusHistory::getTransitionCode).toList();
        assertThat(codes).containsExactly("A1", "A2", "P1", "P2", "M1", "M2", "M3");
    }
}
