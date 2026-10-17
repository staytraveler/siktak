package com.siktak.application.domain.status;

/** 상태 축. 네 축은 별도 컬럼으로 저장하고 하나로 합치지 않는다 (PRD 12장). */
public enum StatusAxis {
    APPLICATION,
    PAYMENT,
    MATCH,
    ATTENDANCE
}
