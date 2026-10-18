package com.siktak.feedback.application;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.application.application.ApplicationService;
import com.siktak.application.domain.status.AttendanceStatus;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.feedback.domain.Attendance;
import com.siktak.feedback.domain.PersonPreference;
import com.siktak.feedback.domain.PreferenceType;
import com.siktak.feedback.infra.AttendanceRepository;
import com.siktak.feedback.infra.PersonPreferenceRepository;
import com.siktak.matching.domain.TableMember;
import com.siktak.matching.infra.TableMemberRepository;
import com.siktak.user.domain.User;
import com.siktak.user.infra.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 사람별 선호 "다시 만나고 싶은/싫은 사람" (FDB-04). 같은 테이블 동석자만 대상이고 이름만 보인다.
 * 선택은 비공개다: 조회는 본인이 쓴 것만, 상대는 누가 어떻게 골랐는지 알 수 없다. {@code AVOID}는 매칭 엔진이 다음 매칭에서 분리한다 (H6).
 * 동석자는 table_members.id로 가리켜 다른 참가자의 사용자 ID를 화면에 내보내지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PersonPreferenceService {

    private final ApplicationService applicationService;
    private final AttendanceRepository attendanceRepository;
    private final TableMemberRepository tableMemberRepository;
    private final PersonPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;

    public record Companion(Long companionId, String name) {
    }

    public record Choice(Long companionId, PreferenceType preference) {
    }

    /** 같은 테이블 동석자 (본인 제외, 이름만). 테이블 구성원만 볼 수 있다. */
    @Transactional(readOnly = true)
    public List<Companion> companions(Long userId, Long applicationId) {
        Attendance attendance = ownAttendance(userId, applicationId);
        List<TableMember> others = otherMembers(attendance);
        Map<Long, User> users = userRepository.findAllById(others.stream().map(TableMember::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return others.stream()
                .map(m -> new Companion(m.getId(), users.get(m.getUserId()).getName()))
                .toList();
    }

    /** 본인이 고른 선호만 돌려준다. */
    @Transactional(readOnly = true)
    public List<Choice> mine(Long userId, Long applicationId) {
        Attendance attendance = ownAttendance(userId, applicationId);
        Map<Long, Long> companionByUser = otherMembers(attendance).stream()
                .collect(Collectors.toMap(TableMember::getUserId, TableMember::getId));
        return preferenceRepository.findByFromUserIdAndDiningTableIdOrderByIdAsc(userId, attendance.getDiningTableId())
                .stream()
                .filter(p -> companionByUser.containsKey(p.getToUserId()))
                .map(p -> new Choice(companionByUser.get(p.getToUserId()), p.getPreference()))
                .toList();
    }

    /**
     * 선호를 통째로 교체한다 (같은 요청을 다시 보내도 결과가 같다). 목록에 없는 동석자의 기존 선택은 지워진다.
     * 체크인한(ATTENDED) 참가자만 남길 수 있고, 같은 사람을 두 번 넣으면 400이다.
     */
    @Transactional
    public List<Choice> replace(Long userId, Long applicationId, List<Choice> choices) {
        Attendance attendance = ownAttendance(userId, applicationId);
        if (attendance.getAttendanceStatus() != AttendanceStatus.ATTENDED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "체크인한 참가자만 선택할 수 있습니다.");
        }
        Map<Long, TableMember> others = otherMembers(attendance).stream()
                .collect(Collectors.toMap(TableMember::getId, Function.identity()));
        Map<Long, PreferenceType> wanted = new LinkedHashMap<>();
        for (Choice c : choices) {
            if (c.companionId() == null || c.preference() == null || !others.containsKey(c.companionId())) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "같은 테이블 동석자만 선택할 수 있습니다.");
            }
            if (wanted.put(others.get(c.companionId()).getUserId(), c.preference()) != null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "같은 사람을 두 번 선택할 수 없습니다.");
            }
        }
        Map<Long, PersonPreference> existing = new HashMap<>();
        preferenceRepository.findByFromUserIdAndDiningTableIdOrderByIdAsc(userId, attendance.getDiningTableId())
                .forEach(p -> existing.put(p.getToUserId(), p));
        existing.forEach((toUserId, p) -> {
            if (!wanted.containsKey(toUserId)) {
                preferenceRepository.delete(p);
            }
        });
        wanted.forEach((toUserId, preference) -> {
            PersonPreference p = existing.get(toUserId);
            if (p == null) {
                preferenceRepository.save(
                        PersonPreference.of(userId, toUserId, attendance.getDiningTableId(), preference));
            } else {
                p.change(preference);
            }
        });
        return mine(userId, applicationId);
    }

    private Attendance ownAttendance(Long userId, Long applicationId) {
        applicationService.getOwned(userId, applicationId);
        return attendanceRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "확정된 테이블이 없습니다."));
    }

    private List<TableMember> otherMembers(Attendance attendance) {
        return tableMemberRepository.findByDiningTableIdAndActiveTrue(attendance.getDiningTableId()).stream()
                .filter(m -> !m.getUserId().equals(attendance.getUserId()))
                .toList();
    }
}
