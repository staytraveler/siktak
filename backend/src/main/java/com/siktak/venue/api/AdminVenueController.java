package com.siktak.venue.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.venue.application.VenueAdminService;
import com.siktak.venue.domain.Venue;
import com.siktak.venue.domain.VenueSlotCapacity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/** 운영자 식당 재고 관리 (PRD 11장, INF-01·03). 권한은 SecurityConfig의 {@code /api/v1/admin/**} 규칙. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminVenueController {

    private final VenueAdminService venueAdminService;

    @GetMapping("/venues")
    public List<VenueResponse> list() {
        return venueAdminService.findAll().stream().map(VenueResponse::from).toList();
    }

    @PostMapping("/venues")
    @ResponseStatus(HttpStatus.CREATED)
    public VenueResponse create(@Valid @RequestBody VenueRequest r) {
        return VenueResponse.from(venueAdminService.create(r.regionId(), r.name(), r.address(), r.mapUrl(),
                r.priceRange(), r.description()));
    }

    @PutMapping("/venues/{id}")
    public VenueResponse update(@PathVariable Long id, @Valid @RequestBody VenueRequest r) {
        return VenueResponse.from(venueAdminService.update(id, r.regionId(), r.name(), r.address(), r.mapUrl(),
                r.priceRange(), r.description(), r.active() == null || r.active()));
    }

    /** 회차의 식당별 수용량 (설정이 없는 식당은 목록에 없다). */
    @GetMapping("/venue-capacities")
    public List<CapacityResponse> capacities(@RequestParam Long slotId) {
        return venueAdminService.capacities(slotId).stream().map(CapacityResponse::from).toList();
    }

    @PutMapping("/venues/{venueId}/capacities/{slotId}")
    public CapacityResponse setCapacity(@PathVariable Long venueId, @PathVariable Long slotId,
            @Valid @RequestBody CapacityRequest r) {
        return CapacityResponse.from(venueAdminService.setMaxTables(venueId, slotId, r.maxTables()));
    }

    public record VenueRequest(@NotNull Long regionId, @NotBlank String name, @NotBlank String address, String mapUrl,
            String priceRange, String description, Boolean active) {
    }

    public record VenueResponse(Long id, Long regionId, String name, String address, String mapUrl, String priceRange,
            String description, boolean active) {

        static VenueResponse from(Venue v) {
            return new VenueResponse(v.getId(), v.getRegionId(), v.getName(), v.getAddress(), v.getMapUrl(),
                    v.getPriceRange(), v.getDescription(), v.isActive());
        }
    }

    public record CapacityRequest(@NotNull @Min(0) Integer maxTables) {
    }

    public record CapacityResponse(Long venueId, Long slotId, int maxTables, int reservedTables,
            int maxSeatsPerTable) {

        static CapacityResponse from(VenueSlotCapacity c) {
            return new CapacityResponse(c.getVenueId(), c.getEventSlotId(), c.getMaxTables(), c.getReservedTables(),
                    c.getMaxSeatsPerTable());
        }
    }
}
