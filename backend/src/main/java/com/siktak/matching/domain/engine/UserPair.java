package com.siktak.matching.domain.engine;

/**
 * 순서 없는 사용자 쌍. 차단·신고·{@code AVOID} 관계와 이전 만남을 표현한다.
 *
 * <p>관계는 한쪽만 선택했어도 양방향으로 취급하므로(H6) 항상 {@link #of}로 정규화해서 만든다.
 */
public record UserPair(long lowUserId, long highUserId) {

    public UserPair {
        if (lowUserId >= highUserId) {
            throw new IllegalArgumentException("lowUserId < highUserId 여야 한다: " + lowUserId + ", " + highUserId);
        }
    }

    public static UserPair of(long userA, long userB) {
        return new UserPair(Math.min(userA, userB), Math.max(userA, userB));
    }
}
