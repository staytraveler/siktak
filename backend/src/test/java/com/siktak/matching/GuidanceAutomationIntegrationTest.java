package com.siktak.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

import com.siktak.application.domain.Application;
import com.siktak.chat.application.ChatService;
import com.siktak.chat.application.TableContentJobHandler;
import com.siktak.chat.application.TableReminderJobHandler;
import com.siktak.chat.domain.ChatMessage;
import com.siktak.chat.domain.ChatMessage.MessageType;
import com.siktak.chat.domain.ChatRoom;
import com.siktak.common.schedule.JobTrigger;
import com.siktak.common.schedule.ScheduledJob;
import com.siktak.common.schedule.ScheduledJobRegistry;
import com.siktak.exception.domain.CaseType;
import com.siktak.matching.domain.DiningTable;
import com.siktak.notification.application.NotificationSendJobHandler;
import com.siktak.notification.application.NotificationService;
import com.siktak.notification.domain.Notification;
import com.siktak.notification.domain.NotificationStatus;
import com.siktak.notification.infra.InAppNotificationSender;
import com.siktak.notification.infra.NotificationRepository;

/**
 * 5.2 모임 안내 자동화: J1 뒤 단체방 공지·큐레이션·정책과 확정 알림, J3 리마인드, J4 대화 콘텐츠,
 * 재실행 시 중복 없음, 발송 실패 주입 시 재시도 뒤 예외 1건 (INF-01·03·05·07·08, CON-01~03).
 */
@Tag("db")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class GuidanceAutomationIntegrationTest extends DemoScenarioDbTestSupport {

    @Autowired private ChatService chatService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private NotificationSendJobHandler sendJobHandler;
    @Autowired private TableReminderJobHandler reminderJobHandler;
    @Autowired private TableContentJobHandler contentJobHandler;
    @Autowired private InAppNotificationSender sender;
    @Autowired private ScheduledJobRegistry jobRegistry;

    @AfterEach
    void reset() {
        sender.setFailInjection(false);
        jobRegistry.replaceAll(List.of());
    }

    @Test
    void J1_뒤_S2_단체방에_공지_큐레이션_정책이_올라가고_확정_알림이_6건_나간다() {
        runJ1();
        Long tableId = tableOf(17);
        List<ChatMessage> messages = messages(tableId);

        assertThat(messages).extracting(ChatMessage::getMessageType)
                .containsExactly(MessageType.NOTICE, MessageType.NOTICE, MessageType.CURATION, MessageType.NOTICE);
        ChatMessage venueNotice = messages.get(1);
        assertThat(venueNotice.getBody()).contains("합정 모노다이닝", "예약명", "집합 시간", "식사 가격", "지도");
        assertThat(venueNotice.getPayload()).containsKeys("venueName", "address", "mapUrl", "reservationName",
                "gatherTime", "priceRange");
        // 큐레이션은 집계만: 개인 이름·점수를 싣지 않는다 (INF-06)
        ChatMessage curation = messages.get(2);
        assertThat(curation.getBody()).contains("6명", "연령대", "성비", "공통 관심사").doesNotContain("참가자", "점수");
        assertThat(messages.get(3).getBody()).contains("환불", "당일 취소", "노쇼");

        // 확정 알림: S2 테이블 6명에게 1건씩
        for (int no = 17; no <= 22; no++) {
            List<Notification> mine = notificationRepository
                    .findByUserIdAndSendStatusOrderByIdDesc(app.get(no).getUserId(), NotificationStatus.SENT).stream()
                    .filter(n -> n.getTemplateCode().equals("MATCH_CONFIRMED")).toList();
            assertThat(mine).as("user%d 확정 알림", no).hasSize(1);
            assertThat(mine.get(0).getBody()).contains("합정 모노다이닝");
        }
        assertThat(notificationService.inbox(app.get(17).getUserId())).extracting(Notification::getTemplateCode)
                .contains("APPLICATION_SUBMITTED", "PAYMENT_PAID", "MATCH_CONFIRMED");

        // 같은 안내를 다시 요청해도 늘지 않는다
        long notifications = notificationRepository.count();
        applicationEventPublisher().publishEvent(new com.siktak.matching.domain.TableGuidanceRequested(tableId));
        assertThat(messages(tableId)).hasSize(4);
        assertThat(notificationRepository.count()).isEqualTo(notifications);
    }

    @Test
    void 식당이_없는_테이블은_장소를_확정_후_안내로_올리고_운영자가_배정하면_장소_공지가_추가된다() {
        runJ1();
        DiningTable noVenue = diningTableRepository.findByEventSlotIdOrderByIdAsc(s1.getId()).stream()
                .filter(t -> !t.hasVenue()).findFirst().orElseThrow();
        assertThat(messages(noVenue.getId())).extracting(ChatMessage::getMessageKey)
                .contains("table-" + noVenue.getId() + ":guide:pending")
                .doesNotContain("table-" + noVenue.getId() + ":guide:venue");
        assertThat(messages(noVenue.getId()).stream().filter(m -> m.getBody().contains("확정 후 안내"))).isNotEmpty();

        capacityRepository.findByVenueIdAndEventSlotId(green.getId(), s1.getId()).orElseThrow().changeMaxTables(1);
        tableVenueService.assignVenue(noVenue.getId(), operatorId);

        List<ChatMessage> after = messages(noVenue.getId());
        assertThat(after).extracting(ChatMessage::getMessageKey)
                .contains("table-" + noVenue.getId() + ":guide:pending", "table-" + noVenue.getId() + ":guide:venue");
        assertThat(after.stream().filter(m -> m.getMessageType() == MessageType.CURATION)).hasSize(1);
        assertThat(after.stream().filter(m -> m.getBody().contains("취소·환불 안내"))).hasSize(1);
    }

    @Test
    void J3_리마인드와_J4_콘텐츠가_작업으로_게시되고_다시_실행해도_중복되지_않는다() {
        contentSeeder.seedIfEmpty();
        runJ1();
        Long tableId = tableOf(17);
        DiningTable table = diningTableRepository.findById(tableId).orElseThrow();
        var slot = eventSlotRepository.findById(table.getEventSlotId()).orElseThrow();

        // 기동 시 적재 대상에 두 작업이 모두 있고, 시각은 행사 1일 전·시작 시각이다
        assertThat(reminderJobHandler.loadPending()).contains(
                new ScheduledJob("REMINDER_D1", tableId, slot.getStartsAt().minus(Duration.ofDays(1))));
        assertThat(contentJobHandler.loadPending()).contains(
                new ScheduledJob("CONTENT_POST", tableId, slot.getStartsAt()));

        // J3: 행사 1일 전
        clock.set(slot.getStartsAt().minus(Duration.ofDays(1)));
        reminderJobHandler.execute(tableId, JobTrigger.tick());
        reminderJobHandler.execute(tableId, JobTrigger.tick());
        List<ChatMessage> reminders = messages(tableId).stream()
                .filter(m -> m.getMessageType() == MessageType.REMINDER).toList();
        assertThat(reminders).hasSize(1);
        assertThat(reminders.get(0).getBody()).contains("합정 모노다이닝", "예약명", "노쇼");
        assertThat(templateCount("REMINDER_D1", 17, 22)).isEqualTo(6);
        assertThat(reminderJobHandler.loadPending()).extracting(ScheduledJob::targetId).doesNotContain(tableId);

        // J4: 행사 시작 시각
        clock.set(slot.getStartsAt());
        contentJobHandler.execute(tableId, JobTrigger.tick());
        contentJobHandler.execute(tableId, JobTrigger.tick());
        List<ChatMessage> contents = messages(tableId).stream()
                .filter(m -> m.getMessageType() == MessageType.CONTENT).toList();
        assertThat(contents).hasSize(1);
        Map<String, Object> payload = contents.get(0).getPayload();
        assertThat((List<?>) payload.get("topics")).hasSizeGreaterThanOrEqualTo(3);
        assertThat(payload.get("icebreaker")).isNotNull();
        assertThat(contentJobHandler.loadPending()).extracting(ScheduledJob::targetId).doesNotContain(tableId);
    }

    @Test
    void 발송_실패를_주입하면_재시도_뒤_예외_처리함에_1건만_남는다() {
        sender.setFailInjection(true);
        Application a = app.get(17);
        Notification n = notificationService.enqueue(a.getUserId(), "REFUND_COMPLETED", Map.of("amount", "9,900원"),
                null, "TEST:fail-injection");
        // 첫 시도 실패 → 대기 상태로 남아 재시도 대상
        assertThat(n.getSendStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(n.getAttempts()).isEqualTo(1);
        assertThat(sendJobHandler.loadPending()).extracting(ScheduledJob::targetId).contains(n.getId());
        assertThat(openCases(CaseType.NOTIFICATION_FAILED)).isEmpty();

        // 재시도 3번(1분 간격)도 실패 → FAILED + 예외 1건
        for (int i = 0; i < 3; i++) {
            clock.set(clock.instant().plus(Duration.ofMinutes(1)));
            sendJobHandler.execute(n.getId(), JobTrigger.tick());
        }
        assertThat(n.getSendStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(n.getAttempts()).isEqualTo(4);
        assertThat(openCases(CaseType.NOTIFICATION_FAILED)).hasSize(1);

        // 더 불러도 늘지 않는다 (멱등)
        sendJobHandler.execute(n.getId(), JobTrigger.tick());
        assertThat(openCases(CaseType.NOTIFICATION_FAILED)).hasSize(1);
        assertThat(notificationService.inbox(a.getUserId())).extracting(Notification::getId)
                .doesNotContain(n.getId());

        // 같은 dedup_key로 다시 만들어도 새 알림이 생기지 않는다
        sender.setFailInjection(false);
        assertThat(notificationService.enqueue(a.getUserId(), "REFUND_COMPLETED", Map.of(), null,
                "TEST:fail-injection").getId()).isEqualTo(n.getId());
    }

    @Test
    void 예약_알림은_시각이_되기_전에는_알림함에_보이지_않는다() {
        Application a = app.get(17);
        Notification n = notificationService.enqueue(a.getUserId(), "NEXT_SLOT_RECOMMEND", Map.of(), null,
                clock.instant().plus(Duration.ofHours(1)), "TEST:scheduled");
        assertThat(n.getSendStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(n.getAttempts()).isZero();
        assertThat(notificationService.inbox(a.getUserId())).extracting(Notification::getId)
                .doesNotContain(n.getId());

        clock.set(clock.instant().plus(Duration.ofHours(1)));
        sendJobHandler.execute(n.getId(), JobTrigger.tick());
        assertThat(n.getSendStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notificationService.inbox(a.getUserId())).extracting(Notification::getId).contains(n.getId());
    }

    // ------------------------------------------------------------------ 도우미

    @Autowired private org.springframework.context.ApplicationEventPublisher publisher;

    private org.springframework.context.ApplicationEventPublisher applicationEventPublisher() {
        return publisher;
    }

    private void runJ1() {
        clock.set(s1.getMatchRunAt());
        matchingService.runForSlot(s1.getId(), null);
    }

    private List<ChatMessage> messages(Long tableId) {
        ChatRoom room = chatService.findByTable(tableId).orElseThrow();
        Long userId = tableMemberRepository.findByDiningTableIdAndActiveTrue(tableId).get(0).getUserId();
        return chatService.messagesFor(room.getId(), userId);
    }

    private int templateCount(String template, int fromNo, int toNo) {
        int count = 0;
        for (int no = fromNo; no <= toNo; no++) {
            count += (int) notificationRepository
                    .findByUserIdAndSendStatusOrderByIdDesc(app.get(no).getUserId(), NotificationStatus.SENT).stream()
                    .filter(n -> n.getTemplateCode().equals(template)).count();
        }
        return count;
    }
}
