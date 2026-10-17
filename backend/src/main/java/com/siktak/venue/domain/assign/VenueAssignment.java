package com.siktak.venue.domain.assign;

/**
 * 테이블 1개의 식당 배정.
 *
 * @param reservationName  예약명 제안
 * @param priceMatchedCount 희망 가격대가 식당 가격대와 맞은 멤버 수 (배정 근거)
 */
public record VenueAssignment(long tableId, long venueId, String reservationName, int priceMatchedCount) {
}
