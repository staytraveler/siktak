package com.siktak.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

import com.siktak.application.domain.Application;
import com.siktak.application.domain.PassType;
import com.siktak.application.domain.status.MatchStatus;
import com.siktak.common.error.BusinessException;
import com.siktak.exception.domain.CaseStatus;
import com.siktak.exception.domain.CaseType;
import com.siktak.matching.application.EngineMatchingService;
import com.siktak.matching.domain.DiningTable;
import com.siktak.matching.domain.TableMember;
import com.siktak.matching.domain.TableStatus;
import com.siktak.profile.application.ProfileService;
import com.siktak.user.application.AuthService.LoginResult;
import com.siktak.user.application.GuestService;
import com.siktak.user.domain.UserRole;

/** 운영자 긴급 수정(MAT-14)과 시연용 "새 참가자로 시작". */
@Tag("db")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class OperatorEditAndGuestIntegrationTest extends DemoScenarioDbTestSupport {

    @Autowired private EngineMatchingService engineService;
    @Autowired private GuestService guestService;
    @Autowired private ProfileService profileService;
    @Autowired private com.siktak.user.application.WithdrawalService withdrawalService;

    private int userNoOf(Long applicationId) {
        return app.entrySet().stream().filter(e -> e.getValue().getId().equals(applicationId))
                .map(java.util.Map.Entry::getKey).findFirst().orElseThrow();
    }

    @Test
    void 운영자는_사유와_함께_참가자를_옮기고_테이블을_해제할_수_있고_이력이_남는다() {
        clock.set(s1.getMatchRunAt());
        matchingService.runForSlot(s1.getId(), null);
        List<DiningTable> tables = diningTableRepository.findByEventSlotIdOrderByIdAsc(s1.getId()).stream()
                .filter(t -> t.getTableStatus() == TableStatus.CONFIRMED).toList();
        assertThat(tables.size()).isGreaterThanOrEqualTo(2);

        // 사유가 없으면 거절
        TableMember any = tableMemberRepository.findByDiningTableIdAndActiveTrue(tables.get(0).getId()).get(0);
        assertThatThrownBy(() -> engineService.operatorMove(operatorId, any.getApplicationId(), tables.get(1).getId(), " "))
                .isInstanceOf(BusinessException.class);

        // 필수 조건을 통과하는 이동을 찾아 실행한다 (연령 차·차단 때문에 거절되는 조합은 건너뛴다)
        boolean moved = false;
        outer:
        for (DiningTable from : tables) {
            for (DiningTable to : tables) {
                if (from.getId().equals(to.getId())) {
                    continue;
                }
                for (TableMember m : tableMemberRepository.findByDiningTableIdAndActiveTrue(from.getId())) {
                    try {
                        engineService.operatorMove(operatorId, m.getApplicationId(), to.getId(), "안전 문제로 자리 조정");
                    } catch (BusinessException e) {
                        continue;
                    }
                    Optional<TableMember> now = tableMemberRepository.findByApplicationIdAndActiveTrue(m.getApplicationId());
                    assertThat(now).hasValueSatisfying(x -> assertThat(x.getDiningTableId()).isEqualTo(to.getId()));
                    assertThat(tableMemberRepository.findByDiningTableIdAndActiveTrue(to.getId()))
                            .hasSize(diningTableRepository.findById(to.getId()).orElseThrow().getMemberCount());
                    moved = true;
                    break outer;
                }
            }
        }
        assertThat(moved).as("필수 조건을 통과하는 이동이 하나는 있어야 한다").isTrue();
        // 이력: 예외 처리함에 처리 완료 + 운영자 + 사유로 남는다
        assertThat(exceptionCaseRepository.findAllByOrderByIdAsc()).anySatisfy(c -> {
            assertThat(c.getCaseType()).isEqualTo(CaseType.MATCH_CONFLICT);
            assertThat(c.getCaseStatus()).isEqualTo(CaseStatus.RESOLVED);
            assertThat(c.getResolvedBy()).isEqualTo(operatorId);
            assertThat(c.getResolutionNote()).contains("참가자 이동").contains("안전 문제로 자리 조정");
        });

        // 테이블 해제: 구성원은 대체 일정 선택으로 넘어가고 테이블은 DISSOLVED
        DiningTable victim = diningTableRepository.findByEventSlotIdOrderByIdAsc(s1.getId()).stream()
                .filter(t -> t.getTableStatus() == TableStatus.CONFIRMED).findFirst().orElseThrow();
        List<TableMember> victims = tableMemberRepository.findByDiningTableIdAndActiveTrue(victim.getId());
        assertThatThrownBy(() -> engineService.operatorDissolve(operatorId, victim.getId(), ""))
                .isInstanceOf(BusinessException.class);
        engineService.operatorDissolve(operatorId, victim.getId(), "신고 접수로 테이블 정리");
        assertThat(diningTableRepository.findById(victim.getId()).orElseThrow().getTableStatus())
                .isEqualTo(TableStatus.DISSOLVED);
        for (TableMember m : victims) {
            Application a = app.get(userNoOf(m.getApplicationId()));
            assertThat(a.getMatchStatus()).isEqualTo(MatchStatus.ALTERNATIVE_OFFERED);
        }
    }

    @Test
    void 새_참가자로_시작하면_매번_다른_계정이_만들어지고_바로_신청할_수_있다() {
        LoginResult a = guestService.start();
        LoginResult b = guestService.start();

        assertThat(a.user().getId()).isNotEqualTo(b.user().getId());
        assertThat(a.user().getLoginId()).isNotEqualTo(b.user().getLoginId());
        assertThat(a.user().getRole()).isEqualTo(UserRole.PARTICIPANT);
        assertThat(a.token().accessToken()).isNotBlank();
        assertThat(profileService.isComplete(a.user().getId())).isTrue();
        assertThat(profileService.isComplete(b.user().getId())).isTrue();
        // 신청 전 상태로 시작하고, 바로 신청·결제할 수 있다
        Application applied = applicationService.submit(a.user().getId(), List.of(s4.getId()), PassType.SINGLE);
        applicationService.approvePayment(a.user().getId(), applied.getId());
        assertThat(applied.getMatchStatus()).isEqualTo(MatchStatus.WAITING);
    }

    /** PRD 16장: 진행 중인 신청이 있으면 탈퇴 불가, 없으면 개인정보 삭제·로그인 차단, 고정 시연 계정은 탈퇴 불가. */
    @Test
    void 탈퇴는_진행_중인_신청이_없을_때만_되고_개인정보가_지워진다() {
        LoginResult g = guestService.start();
        Long id = g.user().getId();
        Application applied = applicationService.submit(id, List.of(s4.getId()), PassType.SINGLE);
        // 결제 대기 중인 신청이 있으면 거절
        assertThatThrownBy(() -> withdrawalService.withdraw(id)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("진행 중인 신청");
        // 시연용 고정 계정은 탈퇴할 수 없다
        assertThatThrownBy(() -> withdrawalService.withdraw(app.get(1).getUserId())).isInstanceOf(BusinessException.class);

        // 신청을 정리하면 탈퇴된다
        LoginResult g2 = guestService.start();
        withdrawalService.withdraw(g2.user().getId());
        var user = userRepository.findById(g2.user().getId()).orElseThrow();
        assertThat(user.getName()).isEqualTo("탈퇴한 회원");
        assertThat(user.getPhone()).isNull();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getAccountStatus()).isEqualTo(com.siktak.user.domain.AccountStatus.BANNED);
        // 프로필 행이 DB에서 지워졌다 (영속성 컨텍스트 캐시를 거치지 않고 직접 센다)
        assertThat(jdbc.queryForObject("select count(*) from profiles where user_id = ?", Long.class, g2.user().getId()))
                .isZero();
        assertThat(applied.getApplicationStatus()).isNotNull();
    }
}
