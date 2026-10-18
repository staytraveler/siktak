package com.siktak.feedback.api;

import java.time.Instant;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.application.domain.status.AttendanceStatus;
import com.siktak.common.security.AuthUser;
import com.siktak.feedback.application.AttendanceService;
import com.siktak.feedback.application.PersonPreferenceService;
import com.siktak.feedback.domain.Attendance;
import com.siktak.feedback.domain.PreferenceType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/** 체크인(FDB-01)과 사람별 선호(FDB-04). 사람별 선호는 본인이 쓴 것만 돌려주고, 동석자는 이름만 보인다. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final PersonPreferenceService preferenceService;

    @GetMapping("/me/applications/{applicationId}/attendance")
    public AttendanceResponse attendance(@AuthenticationPrincipal AuthUser user, @PathVariable Long applicationId) {
        return AttendanceResponse.from(attendanceService.view(user.id(), applicationId));
    }

    /** T2 앱 체크인. 이미 체크인했으면 같은 결과 (멱등). */
    @PostMapping("/me/applications/{applicationId}/check-in")
    public AttendanceResponse checkIn(@AuthenticationPrincipal AuthUser user, @PathVariable Long applicationId) {
        attendanceService.checkIn(user.id(), applicationId);
        return AttendanceResponse.from(attendanceService.view(user.id(), applicationId));
    }

    @GetMapping("/me/applications/{applicationId}/companions")
    public List<PersonPreferenceService.Companion> companions(@AuthenticationPrincipal AuthUser user,
            @PathVariable Long applicationId) {
        return preferenceService.companions(user.id(), applicationId);
    }

    @GetMapping("/me/applications/{applicationId}/person-preferences")
    public List<PreferenceChoice> preferences(@AuthenticationPrincipal AuthUser user,
            @PathVariable Long applicationId) {
        return preferenceService.mine(user.id(), applicationId).stream().map(PreferenceChoice::from).toList();
    }

    /** 선택을 통째로 교체한다. 빈 목록이면 모두 지운다. */
    @PutMapping("/me/applications/{applicationId}/person-preferences")
    public List<PreferenceChoice> replacePreferences(@AuthenticationPrincipal AuthUser user,
            @PathVariable Long applicationId, @Valid @RequestBody PreferenceRequest request) {
        List<PersonPreferenceService.Choice> choices = request.choices().stream()
                .map(c -> new PersonPreferenceService.Choice(c.companionId(), c.preference())).toList();
        return preferenceService.replace(user.id(), applicationId, choices).stream().map(PreferenceChoice::from)
                .toList();
    }

    public record AttendanceResponse(Long applicationId, AttendanceStatus attendanceStatus, Instant checkedInAt,
            Instant checkInOpensAt, Instant checkInClosesAt, boolean canCheckIn) {

        static AttendanceResponse from(AttendanceService.AttendanceView v) {
            Attendance a = v.attendance();
            return new AttendanceResponse(a.getApplicationId(), a.getAttendanceStatus(), a.getCheckedInAt(),
                    v.checkInOpensAt(), v.checkInClosesAt(), v.canCheckIn());
        }
    }

    public record PreferenceRequest(@NotNull List<@Valid PreferenceChoice> choices) {
    }

    public record PreferenceChoice(@NotNull Long companionId, @NotNull PreferenceType preference) {

        static PreferenceChoice from(PersonPreferenceService.Choice c) {
            return new PreferenceChoice(c.companionId(), c.preference());
        }
    }
}
