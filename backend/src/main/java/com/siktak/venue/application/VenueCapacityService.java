package com.siktak.venue.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.infra.EventSlotRepository;
import com.siktak.venue.domain.Venue;
import com.siktak.venue.domain.VenueSlotCapacity;
import com.siktak.venue.infra.VenueRepository;
import com.siktak.venue.infra.VenueSlotCapacityRepository;

import lombok.RequiredArgsConstructor;

/** 운영자 식당 수용량 조회·변경 (식당 부족 예외 처리, PRD 15장). */
@Service
@RequiredArgsConstructor
public class VenueCapacityService {

    /** [추측] 수용량 행이 없던 식당을 운영자가 처음 열 때의 테이블당 좌석 수. PRD는 4~6명 테이블이라 상한 6으로 둔다. */
    static final int DEFAULT_SEATS_PER_TABLE = 6;

    private final EventSlotRepository eventSlotRepository;
    private final VenueRepository venueRepository;
    private final VenueSlotCapacityRepository capacityRepository;

    /** 회차 지역의 활성 식당과 이 회차 수용량. 수용량 행이 없는 식당은 0/0으로 보인다. */
    @Transactional(readOnly = true)
    public List<VenueCapacityView> ofSlot(Long slotId) {
        EventSlot slot = slot(slotId);
        return venueRepository.findByRegionIdAndActiveTrueOrderByIdAsc(slot.getRegion().getId()).stream()
                .map(v -> view(v, capacityRepository.findByVenueIdAndEventSlotId(v.getId(), slotId).orElse(null)))
                .toList();
    }

    /** 수용량을 {@code maxTables}로 바꾼다. 이미 배정된 수보다 줄이면 거절한다. */
    @Transactional
    public VenueCapacityView changeMaxTables(Long slotId, Long venueId, int maxTables) {
        EventSlot slot = slot(slotId);
        Venue venue = venueRepository.findById(venueId)
                .filter(v -> v.getRegionId().equals(slot.getRegion().getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "이 회차 지역의 식당을 찾을 수 없습니다."));
        VenueSlotCapacity capacity = capacityRepository.findByVenueIdAndEventSlotId(venueId, slotId)
                .orElseGet(() -> capacityRepository.save(
                        VenueSlotCapacity.of(venueId, slotId, 0, DEFAULT_SEATS_PER_TABLE)));
        try {
            capacity.changeMaxTables(maxTables);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, e.getMessage());
        }
        return view(venue, capacity);
    }

    private EventSlot slot(Long slotId) {
        return eventSlotRepository.findById(slotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "회차를 찾을 수 없습니다."));
    }

    private static VenueCapacityView view(Venue v, VenueSlotCapacity c) {
        return new VenueCapacityView(v.getId(), v.getName(), c == null ? 0 : c.getMaxTables(),
                c == null ? 0 : c.getReservedTables(), c == null ? DEFAULT_SEATS_PER_TABLE : c.getMaxSeatsPerTable());
    }

    public record VenueCapacityView(Long venueId, String venueName, int maxTables, int reservedTables,
            int maxSeatsPerTable) {
    }
}
