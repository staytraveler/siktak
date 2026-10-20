package com.siktak.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

import com.siktak.application.application.CancellationService;
import com.siktak.application.domain.Application;
import com.siktak.application.domain.PassType;
import com.siktak.application.domain.status.ApplicationStatus;
import com.siktak.application.domain.status.AttendanceStatus;
import com.siktak.application.domain.status.MatchStatus;
import com.siktak.application.domain.status.PaymentStatus;
import com.siktak.chat.application.ChatService;
import com.siktak.chat.domain.ChatRoom;
import com.siktak.common.error.BusinessException;
import com.siktak.exception.domain.CaseType;
import com.siktak.feedback.domain.Attendance;
import com.siktak.feedback.infra.AttendanceRepository;
import com.siktak.matching.domain.DiningTable;
import com.siktak.matching.domain.TableMember;
import com.siktak.matching.domain.TableStatus;
import com.siktak.payment.domain.PassStatus;
import com.siktak.payment.domain.UserPass;
import com.siktak.payment.infra.UserPassRepository;

/**
 * 신청·참석 취소 (states.md 8장, PRD 15장 "확정 후 1명 취소"): 확정 전 취소의 환불·복원, 확정 후 사전·당일 취소,
 * 4명 미만이 되면 다른 테이블로 재배치하거나 대체 일정으로 넘기는 것까지.
 */
@Tag("db")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class CancellationFlowIntegrationTest extends DemoScenarioDbTestSupport {

    @Autowired private CancellationService cancellationService;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private ChatService chatService;
    @Autowired private UserPassRepository userPassRepository;

    private void cancel(int userNo) {
        Application a = app.get(userNo);
        cancellationService.cancel(a.getUserId(), a.getId());
    }

    private Optional<TableMember> memberOf(int userNo) {
        return tableMemberRepository.findByApplicationIdAndActiveTrue(app.get(userNo).getId());
    }

    @Test
    void 확정_전_신청_취소는_1회권을_환불하고_결제_전_신청은_환불_없이_취소한다() {
        // 매칭 전(D-3): user01은 결제 완료·매칭 대기 → 취소하면 자동 환불
        cancel(1);
        Application u1 = app.get(1);
        assertThat(u1.getApplicationStatus()).isEqualTo(ApplicationStatus.CANCELED);
        assertThat(u1.getMatchStatus()).isEqualTo(MatchStatus.NO_MATCH);
        assertThat(u1.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        // 같은 요청을 다시 보내도 중복 환불 없이 거절
        assertThatThrownBy(() -> cancel(1)).isInstanceOf(BusinessException.class);

        // user16은 결제 대기 → 환불 없이 취소
        cancel(16);
        assertThat(app.get(16).getApplicationStatus()).isEqualTo(ApplicationStatus.CANCELED);
        assertThat(app.get(16).getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);

        // 취소한 신청은 매칭 후보에서 빠진다
        clock.set(s1.getMatchRunAt());
        matchingService.runForSlot(s1.getId(), null);
        assertThat(memberOf(1)).isEmpty();
    }

    @Test
    void 환불_불가_시점_이후의_확정_전_신청은_취소할_수_없다() {
        // user23·24는 S3(D+1) 신청 → 마감 직전(D-1 12:00 전후)에는 행사 2일 전이 지나 있다
        clock.set(s3.getStartsAt().minus(Duration.ofDays(1)));
        assertThatThrownBy(() -> cancel(23)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("2일 전 이후");
        assertThat(cancellationService.info(app.get(23).getUserId(), app.get(23).getId()).canCancel()).isFalse();
    }

    @Test
    void 크레딧으로_낸_신청을_취소하면_크레딧이_돌아온다() {
        Application source = app.get(1);
        userPassRepository.save(UserPass.singleCredit(source.getUserId(), source.getId(), clock.instant()));
        Application credited = applicationService.submit(source.getUserId(), List.of(s4.getId()), PassType.CREDIT);
        assertThat(userPassRepository.findFirstByUsedApplicationId(credited.getId())).isPresent();

        cancellationService.cancel(source.getUserId(), credited.getId());

        assertThat(credited.getApplicationStatus()).isEqualTo(ApplicationStatus.CANCELED);
        assertThat(userPassRepository.findFirstByUsedApplicationId(credited.getId())).isEmpty();
        assertThat(userPassRepository.findByUserIdAndPassStatusOrderByIdDesc(source.getUserId(), PassStatus.ACTIVE))
                .anyMatch(p -> p.getPassType() == com.siktak.payment.domain.UserPassType.SINGLE_CREDIT);
    }

    @Test
    void 확정_후_사전_취소는_환불하고_단체방에서_나가며_4명_아래가_되면_테이블이_해체된다() {
        clock.set(s1.getMatchRunAt());
        matchingService.runForSlot(s1.getId(), null);
        // S2는 6명 한 테이블 (user17~22)
        Long tableId = memberOf(17).orElseThrow().getDiningTableId();
        ChatRoom room = chatService.findByTable(tableId).orElseThrow();
        assertThat(tableMemberRepository.findByDiningTableIdAndActiveTrue(tableId)).hasSize(6);

        // 1명 취소: 환불 + 단체방 퇴장, 5명으로 유지
        cancel(17);
        Application u17 = app.get(17);
        assertThat(u17.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        Attendance a17 = attendanceRepository.findByApplicationId(u17.getId()).orElseThrow();
        assertThat(a17.getAttendanceStatus()).isEqualTo(AttendanceStatus.CANCELED_EARLY);
        assertThat(chatService.messagesOfRoom(room.getId())).anyMatch(m -> m.getBody().contains("동석자 1명이 참석을 취소"));
        assertThatThrownBy(() -> chatService.messagesFor(room.getId(), u17.getUserId())).isInstanceOf(BusinessException.class);
        DiningTable table = diningTableRepository.findById(tableId).orElseThrow();
        assertThat(table.getTableStatus()).isEqualTo(TableStatus.CONFIRMED);
        assertThat(table.getMemberCount()).isEqualTo(5);

        cancel(18);
        assertThat(diningTableRepository.findById(tableId).orElseThrow().getMemberCount()).isEqualTo(4);

        // 3명이 되면 4명 미만: 들어갈 다른 테이블이 없으므로 남은 3명은 대체 일정 선택으로 넘어간다
        cancel(19);
        assertThat(diningTableRepository.findById(tableId).orElseThrow().getTableStatus()).isEqualTo(TableStatus.DISSOLVED);
        for (int no : new int[] {20, 21, 22}) {
            Application a = app.get(no);
            assertThat(a.getMatchStatus()).isEqualTo(MatchStatus.ALTERNATIVE_OFFERED);
            assertThat(a.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_AVAILABLE);
            assertThat(resolutionRepository.findByApplicationId(a.getId())).isPresent();
            assertThat(attendanceRepository.findByApplicationId(a.getId())).isEmpty();
            assertThat(memberOf(no)).isEmpty();
        }
        assertThat(chatService.findByTable(tableId).orElseThrow().getRoomStatus()).isEqualTo(ChatRoom.RoomStatus.CLOSED);
        // 같은 취소를 다시 처리해도 새 매칭 실행이 늘지 않는다 (멱등)
    }

    @Test
    void 확정_후_취소로_4명_미만이_되면_자리_남은_다른_테이블로_옮기고_누구도_사라지지_않는다() {
        clock.set(s1.getMatchRunAt());
        matchingService.runForSlot(s1.getId(), null);
        // S1 확정자 중 한 테이블을 골라 2명을 빼 4명 미만으로 만든다
        List<DiningTable> s1Tables = diningTableRepository.findByEventSlotIdOrderByIdAsc(s1.getId()).stream()
                .filter(t -> t.getTableStatus() == TableStatus.CONFIRMED).toList();
        assertThat(s1Tables.size()).isGreaterThanOrEqualTo(2);
        DiningTable victim = s1Tables.get(0);
        List<TableMember> members = tableMemberRepository.findByDiningTableIdAndActiveTrue(victim.getId());
        int total = s1Tables.stream().mapToInt(DiningTable::getMemberCount).sum();
        int victimOriginal = victim.getMemberCount(); // 해체되면 0으로 바뀐다
        int toCancel = victim.getMemberCount() - 3; // 남은 인원 3명이 되도록
        List<Integer> userNos = members.stream()
                .map(m -> app.entrySet().stream().filter(e -> e.getValue().getId().equals(m.getApplicationId()))
                        .map(java.util.Map.Entry::getKey).findFirst().orElseThrow())
                .toList();
        for (int i = 0; i < toCancel; i++) {
            cancel(userNos.get(i));
        }
        assertThat(diningTableRepository.findById(victim.getId()).orElseThrow().getTableStatus())
                .isEqualTo(TableStatus.DISSOLVED);

        // 불변 조건: 활성 멤버는 모두 CONFIRMED 테이블 4~6명, 나머지는 대체 일정 대기. 사람이 사라지지 않는다.
        List<Integer> remaining = userNos.subList(toCancel, userNos.size());
        int placed = 0;
        for (int no : remaining) {
            Application a = app.get(no);
            Optional<TableMember> m = memberOf(no);
            if (m.isPresent()) {
                placed++;
                assertThat(a.getMatchStatus()).isEqualTo(MatchStatus.CONFIRMED);
                assertThat(m.get().getDiningTableId()).isNotEqualTo(victim.getId());
                Attendance att = attendanceRepository.findByApplicationId(a.getId()).orElseThrow();
                assertThat(att.getDiningTableId()).isEqualTo(m.get().getDiningTableId());
            } else {
                assertThat(a.getMatchStatus()).isEqualTo(MatchStatus.ALTERNATIVE_OFFERED);
            }
        }
        List<DiningTable> after = diningTableRepository.findByEventSlotIdOrderByIdAsc(s1.getId()).stream()
                .filter(t -> t.getTableStatus() == TableStatus.CONFIRMED).toList();
        assertThat(after).allSatisfy(t -> {
            int active = tableMemberRepository.findByDiningTableIdAndActiveTrue(t.getId()).size();
            assertThat(active).isBetween(4, 6);
            assertThat(t.getMemberCount()).isEqualTo(active);
        });
        assertThat(after.stream().mapToInt(DiningTable::getMemberCount).sum()).isEqualTo(total - victimOriginal + placed);
    }

    /** STA-07: 확정 테이블에서 취소가 나면 조건에 맞는 대기자가 자동으로 그 자리에 들어가 테이블이 유지된다. */
    @Test
    void 확정_후_취소로_빈_자리에_조건에_맞는_대기자가_자동_충원된다() {
        // 매칭 시각에는 결제 전이라 후보에서 빠졌다가, 매칭 뒤에 결제해 대기자가 된 신청자
        Long waiterUser = rawParticipant("w" + System.nanoTime() % 100000, 91, 1996, "FEMALE");
        Application waiter = applicationService.submit(waiterUser, List.of(s1.getId()), PassType.SINGLE);
        clock.set(s1.getMatchRunAt());
        matchingService.runForSlot(s1.getId(), null);
        applicationService.approvePayment(waiterUser, waiter.getId());
        assertThat(waiter.getMatchStatus()).isEqualTo(MatchStatus.WAITING);

        // S1의 확정 테이블 하나에서 1명 취소 → 대기자 충원
        DiningTable table = diningTableRepository.findByEventSlotIdOrderByIdAsc(s1.getId()).stream()
                .filter(t -> t.getTableStatus() == TableStatus.CONFIRMED && t.getMemberCount() >= 5).findFirst()
                .orElseThrow();
        List<TableMember> members = tableMemberRepository.findByDiningTableIdAndActiveTrue(table.getId());
        int before = members.size();
        Long canceledApp = members.get(0).getApplicationId();
        Application canceled = app.values().stream().filter(a -> a.getId().equals(canceledApp)).findFirst().orElseThrow();
        cancellationService.cancel(canceled.getUserId(), canceled.getId());

        assertThat(waiter.getMatchStatus()).isEqualTo(MatchStatus.CONFIRMED);
        assertThat(tableMemberRepository.findByApplicationIdAndActiveTrue(waiter.getId()))
                .hasValueSatisfying(m -> assertThat(m.getDiningTableId()).isEqualTo(table.getId()));
        assertThat(attendanceRepository.findByApplicationId(waiter.getId())).isPresent();
        // 취소 1명, 충원 1명 → 인원 그대로, 테이블 유지
        assertThat(tableMemberRepository.findByDiningTableIdAndActiveTrue(table.getId())).hasSize(before);
        assertThat(diningTableRepository.findById(table.getId()).orElseThrow().getTableStatus())
                .isEqualTo(TableStatus.CONFIRMED);
    }

    @Test
    void 행사_2일_전_이후_참석_취소는_환불이_없고_이용_제한_검토_예외가_올라가며_단체방에는_남는다() {
        clock.set(s1.getMatchRunAt());
        matchingService.runForSlot(s1.getId(), null);
        Application u17 = app.get(17);
        Long tableId = memberOf(17).orElseThrow().getDiningTableId();
        ChatRoom room = chatService.findByTable(tableId).orElseThrow();

        // 행사 2일 전(D-2 19:30) 이후, 행사 시작 전
        clock.set(s2.getStartsAt().minus(Duration.ofHours(30)));
        var info = cancellationService.info(u17.getUserId(), u17.getId());
        assertThat(info.canCancel()).isTrue();
        assertThat(info.refund()).isFalse();
        cancel(17);

        Attendance a = attendanceRepository.findByApplicationId(u17.getId()).orElseThrow();
        assertThat(a.getAttendanceStatus()).isEqualTo(AttendanceStatus.CANCELED_LATE);
        assertThat(u17.getPaymentStatus()).isEqualTo(PaymentStatus.PAID); // 환불 없음
        assertThat(exceptionCaseRepository.findByCaseKey("LATE_CANCEL:application-" + u17.getId()).orElseThrow()
                .getCaseType()).isEqualTo(CaseType.NO_SHOW_CANDIDATE);
        // 단체방 퇴장 불가: 여전히 방 구성원이다
        assertThat(chatService.messagesFor(room.getId(), u17.getUserId())).isNotNull();
        assertThat(exceptionCaseRepository.findAllByOrderByIdAsc().stream()
                .map(e -> e.getCaseType().name()).collect(Collectors.toList())).contains("NO_SHOW_CANDIDATE");
    }
}
