package com.siktak.matching.domain.engine;

/**
 * 후보 추출(5-1)에서 제외된 신청.
 *
 * <p>{@code ACCOUNT_RESTRICTED}(H2 위반)는 호출 측에서 {@code ExceptionCase}로 남긴다.
 */
public record ExcludedCandidate(long applicationId, long userId, ReasonCode reasonCode) {
}
