package com.siktak.notification.application;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.siktak.common.config.ClockConfig;
import com.siktak.slot.domain.EventSlot;

/** 알림·단체방 안내 문구에 쓰는 날짜·회차 표기. 한국 시간 기준. */
public final class GuidanceFormat {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("M월 d일(E) HH:mm", Locale.KOREAN);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M월 d일(E)", Locale.KOREAN);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN);

    private GuidanceFormat() {
    }

    /** 예: {@code 10월 21일(수) 19:30 성수}. */
    public static String slotLabel(EventSlot slot) {
        return dateTime(slot.getStartsAt()) + " " + slot.getRegion().getName();
    }

    public static String dateTime(Instant at) {
        return DATE_TIME.format(at.atZone(ClockConfig.ZONE));
    }

    public static String date(Instant at) {
        return DATE.format(at.atZone(ClockConfig.ZONE));
    }

    public static String time(Instant at) {
        return TIME.format(at.atZone(ClockConfig.ZONE));
    }

    /** venues.price_range 코드를 참가자 문구로 바꾼다 (matching.md 4장). */
    public static String priceRange(String code) {
        if (code == null) {
            return "현장 안내";
        }
        return switch (code) {
            case "UNDER_20K" -> "1인 2만 원 미만";
            case "20K_40K" -> "1인 2만~4만 원";
            case "OVER_40K" -> "1인 4만 원 이상";
            default -> code;
        };
    }
}
