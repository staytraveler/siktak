package com.siktak.venue.application;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.slot.infra.EventSlotRepository;
import com.siktak.slot.infra.RegionRepository;
import com.siktak.venue.domain.Venue;
import com.siktak.venue.domain.VenueSlotCapacity;
import com.siktak.venue.infra.VenueRepository;
import com.siktak.venue.infra.VenueSlotCapacityRepository;

import lombok.RequiredArgsConstructor;

/** 운영자 식당·수용량 관리 (PRD 11장 식당 재고 관리, 8.2). 배정 로직은 {@link VenueAssignmentService}. */
@Service
@RequiredArgsConstructor
public class VenueAdminService {

    public static final List<String> PRICE_RANGES = List.of("UNDER_20K", "20K_40K", "OVER_40K");
    /** 수용량을 새로 만들 때의 테이블당 좌석 (matching.md 정원 상한과 같은 6). */
    private static final int DEFAULT_SEATS_PER_TABLE = 6;

    private final VenueRepository venueRepository;
    private final VenueSlotCapacityRepository capacityRepository;
    private final RegionRepository regionRepository;
    private final EventSlotRepository eventSlotRepository;

    @Transactional(readOnly = true)
    public List<Venue> findAll() {
        return venueRepository.findAll().stream().sorted(Comparator.comparing(Venue::getId)).toList();
    }

    @Transactional
    public Venue create(Long regionId, String name, String address, String mapUrl, String priceRange,
            String description) {
        checkRegion(regionId);
        checkPriceRange(priceRange);
        return venueRepository.save(Venue.create(regionId, name, address, mapUrl, priceRange, description));
    }

    @Transactional
    public Venue update(Long id, Long regionId, String name, String address, String mapUrl, String priceRange,
            String description, boolean active) {
        checkRegion(regionId);
        checkPriceRange(priceRange);
        Venue venue = venueRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "식당을 찾을 수 없습니다."));
        venue.update(regionId, name, address, mapUrl, priceRange, description, active);
        return venue;
    }

    @Transactional(readOnly = true)
    public List<VenueSlotCapacity> capacities(Long slotId) {
        return capacityRepository.findByEventSlotIdOrderByVenueIdAsc(slotId);
    }

    /** 회차별 수용 테이블 수를 정한다. 없으면 만들고, 있으면 고친다. 이미 배정된 수보다 줄일 수 없다. */
    @Transactional
    public VenueSlotCapacity setMaxTables(Long venueId, Long slotId, int maxTables) {
        venueRepository.findById(venueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "식당을 찾을 수 없습니다."));
        eventSlotRepository.findById(slotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "회차를 찾을 수 없습니다."));
        VenueSlotCapacity capacity = capacityRepository.findByVenueIdAndEventSlotId(venueId, slotId)
                .orElseGet(() -> VenueSlotCapacity.of(venueId, slotId, 0, DEFAULT_SEATS_PER_TABLE));
        try {
            capacity.changeMaxTables(maxTables);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.CONFLICT, e.getMessage());
        }
        return capacityRepository.save(capacity);
    }

    private void checkRegion(Long regionId) {
        regionRepository.findById(regionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "지역을 찾을 수 없습니다."));
    }

    private static void checkPriceRange(String priceRange) {
        if (priceRange != null && !PRICE_RANGES.contains(priceRange)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "가격대는 " + PRICE_RANGES + " 중 하나여야 합니다.");
        }
    }
}
