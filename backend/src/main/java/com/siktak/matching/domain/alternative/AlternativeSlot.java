package com.siktak.matching.domain.alternative;

/** 추천 회차 1건과 추천 이유. */
public record AlternativeSlot(long slotId, Reason reason) {

    public enum Reason {
        CHOSEN_REGION("선택하신 지역"),
        OTHER_REGION("다른 지역");

        private final String text;

        Reason(String text) {
            this.text = text;
        }

        public String text() {
            return text;
        }
    }
}
