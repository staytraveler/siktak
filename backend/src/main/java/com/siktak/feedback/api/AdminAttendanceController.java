package com.siktak.feedback.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.application.domain.status.AttendanceStatus;
import com.siktak.feedback.application.AttendanceService;
import com.siktak.feedback.domain.Attendance;
import com.siktak.user.domain.User;
import com.siktak.user.infra.UserRepository;

import lombok.RequiredArgsConstructor;

/** 운영자 참석 관리 조회 (화면은 8.x). 노쇼 후보 확정(T5)은 8.x에서 붙는다. */
@RestController
@RequestMapping("/api/v1/admin/slots/{slotId}/attendances")
@RequiredArgsConstructor
public class AdminAttendanceController {

    private final AttendanceService attendanceService;
    private final UserRepository userRepository;

    @GetMapping
    public List<AdminAttendanceResponse> list(@PathVariable Long slotId) {
        List<Attendance> attendances = attendanceService.ofSlot(slotId);
        Map<Long, User> users = userRepository.findAllById(attendances.stream().map(Attendance::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return attendances.stream().map(a -> new AdminAttendanceResponse(a.getId(), a.getApplicationId(),
                a.getDiningTableId(), a.getUserId(), users.get(a.getUserId()).getLoginId(),
                users.get(a.getUserId()).getName(), a.getAttendanceStatus(), a.isNoShowCandidate(),
                a.getCheckedInAt())).toList();
    }

    public record AdminAttendanceResponse(Long id, Long applicationId, Long diningTableId, Long userId,
            String loginId, String name, AttendanceStatus attendanceStatus, boolean noShowCandidate,
            Instant checkedInAt) {
    }
}
