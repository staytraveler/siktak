package com.siktak.content.domain;

import com.siktak.profile.domain.Interest;

/**
 * 테이블에 게시할 콘텐츠 1건과 고른 이유.
 *
 * @param interest {@code COMMON_INTEREST}일 때 어떤 공통 관심사로 골랐는지. 그 밖에는 {@code null}
 */
public record SelectedContent(long contentId, ContentType type, Reason reason, Interest interest) {

    public enum Reason {
        /** 테이블 공통 관심사로 고른 주제. */
        COMMON_INTEREST,
        /** 공통 관심사 주제가 3개 미만이라 일반 주제로 채움 (CON-02 최소 3개). */
        GENERAL_FILL,
        /** 아이스브레이킹. */
        ICEBREAKER
    }
}
