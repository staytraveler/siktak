package com.siktak.safety.domain;

/** 운영자가 신고를 처리하는 방법. DISMISS는 조치 없이 기각. */
public enum ResolveAction {
    DISMISS,
    WARNING,
    RESTRICTION,
    PERMANENT_BAN
}
