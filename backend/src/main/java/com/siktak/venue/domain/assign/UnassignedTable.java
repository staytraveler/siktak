package com.siktak.venue.domain.assign;

/**
 * 식당을 배정하지 못한 테이블. 호출 측이 {@code ExceptionCase}로 남긴다.
 *
 * @param reservationName 운영자가 수동 배정할 때 쓸 예약명 제안
 */
public record UnassignedTable(long tableId, UnassignedReason reason, String reservationName) {
}
