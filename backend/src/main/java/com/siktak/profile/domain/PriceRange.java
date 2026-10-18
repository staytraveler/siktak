package com.siktak.profile.domain;

/** 희망 식사 가격대 (matching.md 4장). DB 코드는 프로필·식당 공통이라 {@code code()}를 저장한다. */
public enum PriceRange {
    UNDER_20K("UNDER_20K", "2만 원 미만"),
    MID_20K_40K("20K_40K", "2~4만 원"),
    OVER_40K("OVER_40K", "4만 원 이상");

    private final String code;
    private final String label;

    PriceRange(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static PriceRange fromCode(String code) {
        for (PriceRange r : values()) {
            if (r.code.equals(code)) {
                return r;
            }
        }
        return null;
    }
}
