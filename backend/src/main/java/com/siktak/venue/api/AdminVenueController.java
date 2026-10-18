package com.siktak.venue.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.venue.application.VenueCapacityService;
import com.siktak.venue.application.VenueCapacityService.VenueCapacityView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/** 운영자 회차별 식당 수용량 (식당 부족 예외 처리용). */
@RestController
@RequestMapping("/api/v1/admin/slots/{slotId}/venue-capacities")
@RequiredArgsConstructor
public class AdminVenueController {

    private final VenueCapacityService capacityService;

    @GetMapping
    public List<VenueCapacityView> list(@PathVariable Long slotId) {
        return capacityService.ofSlot(slotId);
    }

    @PutMapping("/{venueId}")
    public VenueCapacityView change(@PathVariable Long slotId, @PathVariable Long venueId,
            @Valid @RequestBody ChangeRequest request) {
        return capacityService.changeMaxTables(slotId, venueId, request.maxTables());
    }

    public record ChangeRequest(@Min(0) int maxTables) {
    }
}
