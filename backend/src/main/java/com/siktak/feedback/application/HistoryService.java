package com.siktak.feedback.application;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.application.application.ApplicationService;
import com.siktak.application.domain.Application;
import com.siktak.application.domain.status.ApplicationStatus;
import com.siktak.application.domain.status.AttendanceStatus;
import com.siktak.application.domain.status.MatchStatus;
import com.siktak.common.config.ClockConfig;
import com.siktak.feedback.domain.Attendance;
import com.siktak.feedback.domain.Feedback;
import com.siktak.feedback.infra.AttendanceRepository;
import com.siktak.feedback.infra.FeedbackRepository;
import com.siktak.matching.domain.DiningTable;
import com.siktak.matching.infra.DiningTableRepository;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.domain.SlotStatus;
import com.siktak.slot.infra.EventSlotRepository;
import com.siktak.venue.domain.Venue;
import com.siktak.venue.infra.VenueRepository;

import lombok.RequiredArgsConstructor;

/**
 * 참가 이력(RET-01)과 다음 일정 추천(RET-02). 본인 기록만 다루고 다른 참가자 정보는 담지 않는다.
 */
@Service
@RequiredArgsConstructor
public class HistoryService {

    private static final int MAX_RECOMMENDATIONS = 3;

    private final AttendanceRepository attendanceRepository;
    private final FeedbackRepository feedbackRepository;
    private final DiningTableRepository diningTableRepository;
    private final EventSlotRepository eventSlotRepository;
    private final VenueRepository venueRepository;
    private final ApplicationService applicationService;
    private final Clock clock;

    public record HistoryItem(Long applicationId, Long eventSlotId, Instant startsAt, String regionName,
            String venueName, AttendanceStatus attendanceStatus, boolean feedbackSubmitted) {
    }

    public record Recommendation(Long eventSlotId, Instant startsAt, Long regionId, String regionName,
            boolean sameRegion, boolean sameTimeSlot) {
    }

    /** 종료된(DONE) 회차의 참석 기록. 최근 회차가 먼저. */
    @Transactional(readOnly = true)
    public List<HistoryItem> history(Long userId) {
        List<Attendance> attendances = attendanceRepository.findByUserIdOrderByIdDesc(userId);
        Map<Long, DiningTable> tables = diningTableRepository
                .findAllById(attendances.stream().map(Attendance::getDiningTableId).distinct().toList()).stream()
                .collect(Collectors.toMap(DiningTable::getId, Function.identity()));
        Map<Long, EventSlot> slots = eventSlotRepository
                .findByIdIn(tables.values().stream().map(DiningTable::getEventSlotId).distinct().toList()).stream()
                .collect(Collectors.toMap(EventSlot::getId, Function.identity()));
        Map<Long, Venue> venues = venueRepository
                .findAllById(tables.values().stream().map(DiningTable::getVenueId).filter(java.util.Objects::nonNull)
                        .distinct().toList())
                .stream().collect(Collectors.toMap(Venue::getId, Function.identity()));
        Set<Long> feedbackApplications = feedbackRepository.findByUserIdOrderByIdDesc(userId).stream()
                .map(Feedback::getApplicationId).collect(Collectors.toSet());
        return attendances.stream()
                .filter(a -> slots.get(tables.get(a.getDiningTableId()).getEventSlotId()).getSlotStatus() == SlotStatus.DONE)
                .map(a -> {
                    DiningTable table = tables.get(a.getDiningTableId());
                    EventSlot slot = slots.get(table.getEventSlotId());
                    Venue venue = table.getVenueId() == null ? null : venues.get(table.getVenueId());
                    return new HistoryItem(a.getApplicationId(), slot.getId(), slot.getStartsAt(),
                            slot.getRegion().getName(), venue == null ? null : venue.getName(), a.getAttendanceStatus(),
                            feedbackApplications.contains(a.getApplicationId()));
                })
                .sorted(Comparator.comparing(HistoryItem::startsAt).reversed())
                .toList();
    }

    /**
     * 다음 일정 추천 (RET-02): 모집 중 회차를 ① 이전 참가 지역 ② 같은 요일·시간대 ③ 날짜 순으로 최대 3개.
     * "이전 참가"는 실제로 체크인한(ATTENDED) 회차. 이미 신청한 회차는 뺀다.
     * [해석] 시간대는 시작 시각의 오전(12시 전)·오후(17시 전)·저녁으로 나눈 구간이 같은 것.
     */
    @Transactional(readOnly = true)
    public List<Recommendation> recommendations(Long userId) {
        Instant now = Instant.now(clock);
        List<Attendance> attended = attendanceRepository.findByUserIdOrderByIdDesc(userId).stream()
                .filter(a -> a.getAttendanceStatus() == AttendanceStatus.ATTENDED).toList();
        Map<Long, DiningTable> tables = diningTableRepository
                .findAllById(attended.stream().map(Attendance::getDiningTableId).distinct().toList()).stream()
                .collect(Collectors.toMap(DiningTable::getId, Function.identity()));
        List<EventSlot> pastSlots = eventSlotRepository
                .findByIdIn(tables.values().stream().map(DiningTable::getEventSlotId).distinct().toList());
        Set<Long> pastRegions = pastSlots.stream().map(s -> s.getRegion().getId()).collect(Collectors.toSet());
        Set<String> pastTimeSlots = pastSlots.stream().map(s -> timeSlotKey(s.getStartsAt()))
                .collect(Collectors.toSet());

        Set<Long> applied = new HashSet<>();
        for (Application a : applicationService.findMine(userId)) {
            boolean live = a.getApplicationStatus() == ApplicationStatus.SUBMITTED
                    && a.getMatchStatus() != MatchStatus.NO_MATCH && a.getMatchStatus() != MatchStatus.TRANSFERRED;
            if (live) {
                a.getSlotChoices().forEach(c -> applied.add(c.getEventSlotId()));
            }
        }

        return eventSlotRepository.findBySlotStatusOrderByStartsAtAscIdAsc(SlotStatus.OPEN).stream()
                .filter(s -> s.isOpenForApplication(now) && !applied.contains(s.getId()))
                .map(s -> new Recommendation(s.getId(), s.getStartsAt(), s.getRegion().getId(), s.getRegion().getName(),
                        pastRegions.contains(s.getRegion().getId()), pastTimeSlots.contains(timeSlotKey(s.getStartsAt()))))
                .sorted(Comparator.comparing(Recommendation::sameRegion).reversed()
                        .thenComparing(Comparator.comparing(Recommendation::sameTimeSlot).reversed())
                        .thenComparing(Recommendation::startsAt))
                .limit(MAX_RECOMMENDATIONS)
                .toList();
    }

    private static String timeSlotKey(Instant at) {
        ZonedDateTime z = at.atZone(ClockConfig.ZONE);
        DayOfWeek day = z.getDayOfWeek();
        int hour = z.getHour();
        String part = hour < 12 ? "AM" : hour < 17 ? "PM" : "EVE";
        return day + "-" + part;
    }
}
