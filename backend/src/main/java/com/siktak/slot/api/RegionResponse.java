package com.siktak.slot.api;

import com.siktak.slot.domain.Region;

public record RegionResponse(Long id, String name) {

    public static RegionResponse from(Region region) {
        return new RegionResponse(region.getId(), region.getName());
    }
}
