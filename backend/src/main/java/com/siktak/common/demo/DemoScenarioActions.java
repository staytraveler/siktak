package com.siktak.common.demo;

import java.util.List;

import org.springframework.stereotype.Service;

import com.siktak.application.application.ApplicationService;
import com.siktak.application.domain.PassType;
import com.siktak.application.domain.status.AttendanceStatus;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.feedback.application.AttendanceService;
import com.siktak.feedback.application.FeedbackService;
import com.siktak.feedback.domain.Attendance;
import com.siktak.user.domain.User;
import com.siktak.user.infra.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 시연용 일괄 동작 (docs/demo-scenario.md 1장). 실제 참가자 API가 쓰는 서비스를 그대로 호출하므로
 * 체크인 허용 시간 같은 규칙이 똑같이 적용된다 (규칙에 걸리면 건너뛴 것으로 센다).
 */
@Service
@RequiredArgsConstructor
public class DemoScenarioActions {

    /** 체크인 일괄에서 빠지는 노쇼 시연 계정 */
    static final String NO_SHOW_LOGIN = "user13";
    static final List<String> ROUND_ONE_SLOTS = List.of("S1", "S2");
    static final List<String> NEW_ROUND_TWO_LOGINS = List.of("user25", "user26");

    private final DemoService demoService;
    private final AttendanceService attendanceService;
    private final FeedbackService feedbackService;
    private final ApplicationService applicationService;
    private final UserRepository userRepository;

    public record Result(String action, int done, int skipped, String message) {
    }

    /** 1회차(S1·S2) 확정자 중 user13을 제외한 전원 체크인. 시간 규칙에 안 맞거나 이미 체크인했으면 건너뛴다. */
    public Result checkInAll() {
        demoService.requireEnabled();
        int done = 0;
        int skipped = 0;
        for (Attendance a : roundOneAttendances()) {
            if (a.getAttendanceStatus() != AttendanceStatus.SCHEDULED || loginOf(a).equals(NO_SHOW_LOGIN)) {
                skipped++;
                continue;
            }
            try {
                attendanceService.checkIn(a.getUserId(), a.getApplicationId());
                done++;
            } catch (BusinessException e) {
                skipped++;
            }
        }
        return new Result("check-in-all", done, skipped,
                done + "명 체크인 (" + NO_SHOW_LOGIN + " 제외, " + skipped + "명 건너뜀)");
    }

    /** 체크인한 참가자 중 피드백이 없는 사람에게 기본 피드백(4점대)을 넣는다. */
    public Result feedbackAll() {
        demoService.requireEnabled();
        int done = 0;
        int skipped = 0;
        for (Attendance a : roundOneAttendances()) {
            if (a.getAttendanceStatus() != AttendanceStatus.ATTENDED || feedbackService.hasFeedback(a.getApplicationId())) {
                skipped++;
                continue;
            }
            feedbackService.submit(a.getUserId(), a.getApplicationId(), 4, 4, 4, 4, 8, "(데모) 기본 피드백");
            done++;
        }
        return new Result("feedback-all", done, skipped, done + "명에게 기본 피드백 입력 (" + skipped + "명 건너뜀)");
    }

    /** 1회차 S1 참석자 전원 + user25·user26이 S4에 신청하고 모의 결제를 마친다. 이미 신청한 사람은 건너뛴다. */
    public Result round2Applications() {
        demoService.requireEnabled();
        Long s1 = demoService.slot("S1").getId();
        Long s4 = demoService.slot("S4").getId();
        List<Long> userIds = new java.util.ArrayList<>(attendanceService.ofSlot(s1).stream()
                .filter(a -> a.getAttendanceStatus() == AttendanceStatus.ATTENDED)
                .map(Attendance::getUserId).toList());
        for (String login : NEW_ROUND_TWO_LOGINS) {
            userIds.add(userRepository.findByLoginId(login)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, login + " 계정이 없습니다. 시드를 초기화하세요."))
                    .getId());
        }
        int done = 0;
        int skipped = 0;
        for (Long userId : userIds) {
            try {
                var application = applicationService.submit(userId, List.of(s4), PassType.SINGLE);
                applicationService.approvePayment(userId, application.getId());
                done++;
            } catch (BusinessException e) {
                skipped++;
            }
        }
        return new Result("round2-applications", done, skipped, done + "명 S4 신청·결제 (" + skipped + "명 건너뜀)");
    }

    private List<Attendance> roundOneAttendances() {
        return ROUND_ONE_SLOTS.stream().flatMap(code -> attendanceService.ofSlot(demoService.slot(code).getId()).stream())
                .toList();
    }

    private String loginOf(Attendance a) {
        return userRepository.findById(a.getUserId()).map(User::getLoginId).orElse("");
    }
}
