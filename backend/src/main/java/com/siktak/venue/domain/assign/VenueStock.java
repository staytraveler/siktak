package com.siktak.venue.domain.assign;

/**
 * 회차 1개에 대한 식당 재고 ({@code venues} + {@code venue_slot_capacities}).
 *
 * @param remainingTables {@code max_tables - reserved_tables}
 * @param priceRange      {@code venues.price_range}. 없으면 {@code null}
 */
public record VenueStock(long venueId, int remainingTables, int maxSeatsPerTable, String priceRange, boolean active) {
}
