package com.siktak.venue.application;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.venue.domain.Venue;
import com.siktak.venue.domain.VenueSlotCapacity;
import com.siktak.venue.infra.VenueRepository;
import com.siktak.venue.infra.VenueSlotCapacityRepository;

import lombok.RequiredArgsConstructor;

/**
 * demo-v1 시드의 식당·회차별 수용량 (demo-scenario.md 2장). 가상 식당이다.
 * V1의 demo-v1 시드가 지역·회차를 만든 뒤 이 메서드를 부른다 (v2-to-pm.md 연결 요청).
 *
 * <p>성수 그린키친은 S1 수용량을 0으로 둬서 S1 테이블 1개가 {@code VENUE_UNAVAILABLE} 예외가 되게 한다 (시연 6단계).
 */
@Component
@RequiredArgsConstructor
public class DemoVenueSeed {

    /** 가격대 코드 (matching.md 4장). */
    public static final String UNDER_20K = "UNDER_20K";
    public static final String MID_20K_40K = "20K_40K";
    public static final String OVER_40K = "OVER_40K";

    private static final int SEATS_PER_TABLE = 6;

    private final VenueRepository venueRepository;
    private final VenueSlotCapacityRepository capacityRepository;

    /**
     * @param slotIdByCode {@code "S1"}~{@code "S5"} → 회차 ID
     */
    @Transactional
    public List<Venue> seed(Long seongsuRegionId, Long hongdaeRegionId, Long gangnamRegionId,
            Map<String, Long> slotIdByCode) {
        Venue oak = venueRepository.save(Venue.create(seongsuRegionId, "성수 오크테이블", "서울 성동구 성수이로 12 (가상)",
                null, MID_20K_40K, "따뜻한 원목 인테리어의 한식 다이닝. 6인 테이블이 넉넉해요."));
        Venue green = venueRepository.save(Venue.create(seongsuRegionId, "성수 그린키친", "서울 성동구 연무장길 34 (가상)",
                null, UNDER_20K, "제철 채소로 만드는 브런치·파스타 비스트로."));
        Venue mono = venueRepository.save(Venue.create(hongdaeRegionId, "합정 모노다이닝", "서울 마포구 양화로 56 (가상)",
                null, MID_20K_40K, "조용한 분위기의 모던 이탈리안. 대화하기 좋은 룸 좌석."));
        Venue brick = venueRepository.save(Venue.create(gangnamRegionId, "강남 브릭하우스", "서울 강남구 테헤란로 78 (가상)",
                null, OVER_40K, "벽돌 인테리어의 그릴 하우스. 모임 코스가 있어요."));

        capacity(oak, slotIdByCode, "S1", 2);
        capacity(oak, slotIdByCode, "S3", 1);
        capacity(oak, slotIdByCode, "S4", 2);
        capacity(green, slotIdByCode, "S1", 0);
        capacity(green, slotIdByCode, "S3", 0);
        capacity(green, slotIdByCode, "S4", 2);
        capacity(mono, slotIdByCode, "S2", 1);
        capacity(brick, slotIdByCode, "S5", 2);
        return List.of(oak, green, mono, brick);
    }

    private void capacity(Venue venue, Map<String, Long> slotIdByCode, String code, int maxTables) {
        Long slotId = slotIdByCode.get(code);
        if (slotId == null) {
            throw new IllegalArgumentException("시드 회차 ID가 없다: " + code);
        }
        capacityRepository.save(VenueSlotCapacity.of(venue.getId(), slotId, maxTables, SEATS_PER_TABLE));
    }
}
