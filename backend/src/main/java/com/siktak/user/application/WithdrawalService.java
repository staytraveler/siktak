package com.siktak.user.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.user.domain.User;
import com.siktak.user.infra.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 탈퇴·개인정보 삭제 (PRD 16장 "개인정보 삭제 요청과 탈퇴 후 보관 정책", docs/privacy-policy.md).
 * <ul>
 *   <li>진행 중인 신청(결제 대기·매칭 대기·확정·대체 일정 선택 대기·환불 처리 중)이 있으면 먼저 정리해야 한다.</li>
 *   <li>탈퇴하면 이름·연락처·프로필을 지우고 로그인을 막는다. 결제·매칭·신고·피드백 기록은 식별 정보 없이 보관한다.</li>
 *   <li>[해석] 시연용 고정 계정(demo01·user01 등)은 심사 흐름이 깨지지 않게 탈퇴할 수 없고, "새 참가자로 시작"한 계정만 탈퇴할 수 있다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbc;

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (!user.getLoginId().startsWith(GuestService.LOGIN_PREFIX)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "시연용 고정 계정은 탈퇴할 수 없어요. 로그인 화면의 \"새 참가자로 시작하기\"로 만든 계정은 탈퇴를 체험할 수 있어요.");
        }
        Long active = jdbc.queryForObject("""
                select count(*) from applications
                where user_id = ? and application_status = 'SUBMITTED'
                  and (match_status in ('WAITING','MATCHING','REALLOCATING','CONFIRMED','ALTERNATIVE_OFFERED','EXCEPTION')
                       or payment_status in ('PENDING','FAILED','REFUND_REQUESTED','REFUND_PROCESSING','REFUND_FAILED'))
                """, Long.class, userId);
        if (active != null && active > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "진행 중인 신청이 있어요. 신청을 취소하거나 모임이 끝난 뒤에 탈퇴할 수 있어요.");
        }
        jdbc.update("delete from profiles where user_id = ?", userId);
        user.withdraw();
    }
}
