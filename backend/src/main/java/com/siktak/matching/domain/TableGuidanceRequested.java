package com.siktak.matching.domain;

/**
 * 테이블이 확정되고 식당 배정까지 시도된 직후, 또는 운영자가 식당을 재배정한 직후에 발행한다.
 * 단체방 공지·확정 알림·리마인드·콘텐츠 예약을 맡은 쪽이 받아 처리한다 (INF-03·05·07, CON-03). 멱등이다.
 */
public record TableGuidanceRequested(Long tableId) {
}
