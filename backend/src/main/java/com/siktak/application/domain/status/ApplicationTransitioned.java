package com.siktak.application.domain.status;

/**
 * 신청의 상태가 실제로 바뀐 뒤 발행하는 도메인 이벤트 (PRD 14장 알림의 계기).
 * 같은 트랜잭션에서 동기로 처리해, 알림이 상태 변경과 함께 저장되거나 함께 취소된다.
 */
public record ApplicationTransitioned(Long applicationId, Long userId, Transition transition, Enum<?> target) {
}
