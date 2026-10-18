package com.siktak.feedback.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.application.domain.status.AttendanceStatus;
import com.siktak.common.security.AuthUser;
import com.siktak.feedback.application.AttendanceService;
import com.siktak.feedback.domain.Attendance;
import com.siktak.user.domain.User;
import com.siktak.user.infra.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/** 운영자 참석 관리: 조회와 노쇼 후보 확정(T5, 8.1). */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminAttendanceController {

    private final AttendanceService attendanceService;
    private final UserRepository userRepository;

    @GetMapping("/slots/{slotId}/attendances")
    public List<AdminAttendanceResponse> list(@PathVariable Long slotId) {
        List<Attendance> attendances = attendanceService.ofSlot(slotId);
        Map<Long, User> users = userRepository.findAllById(attendances.stream().map(Attendance::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return attendances.stream().map(a -> new AdminAttendanceResponse(a.getId(), a.getApplicationId(),
                a.getDiningTableId(), a.getUserId(), users.get(a.getUserId()).getLoginId(),
                users.get(a.getUserId()).getName(), a.getAttendanceStatus(), a.isNoShowCandidate(),
                a.getCheckedInAt())).toList();
    }

    /** T5: 미체크인 노쇼 후보를 노쇼로 확정한다 (사유 필수). 이미 노쇼면 그대로 (멱등). */
    @PostMapping("/attendances/{attendanceId}/confirm-no-show")
    public AttendanceActionResponse confirmNoShow(@AuthenticationPrincipal AuthUser operator,
            @PathVariable Long attendanceId, @Valid @RequestBody ReasonRequest request) {
        Attendance a = attendanceService.confirmNoShow(attendanceId, operator.id(), request.reason());
        return new AttendanceActionResponse(a.getId(), a.getAttendanceStatus(), a.isNoShowCandidate());
    }

    /** 노쇼 후보 해제 (사유 필수). */
    @PostMapping("/attendances/{attendanceId}/dismiss-no-show")
    public AttendanceActionResponse dismissNoShow(@AuthenticationPrincipal AuthUser operator,
            @PathVariable Long attendanceId, @Valid @RequestBody ReasonRequest request) {
        Attendance a = attendanceService.dismissNoShowCandidate(attendanceId, operator.id(), request.reason());
        return new AttendanceActionResponse(a.getId(), a.getAttendanceStatus(), a.isNoShowCandidate());
    }

    public record ReasonRequest(@NotBlank String reason) {
    }

    public record AttendanceActionResponse(Long id, AttendanceStatus attendanceStatus, boolean noShowCandidate) {
    }

    public record AdminAttendanceResponse(Long id, Long applicationId, Long diningTableId, Long userId,
            String loginId, String name, AttendanceStatus attendanceStatus, boolean noShowCandidate,
            Instant checkedInAt) {
    }
}
