package com.siktak.matching.domain.engine;

/**
 * 배치·재배치·실패 사유 코드와 참가자에게 보여줄 문구 (matching.md 7-2).
 *
 * <p>참가자 문구에는 다른 참가자 정보와 상세 점수를 넣지 않는다. {@code BLOCKED_PAIR}는 차단 사실이 드러나지 않게
 * 일반 문구를 쓴다 (MAT-12).
 */
public enum ReasonCode {
    PLACED_FIRST_CHOICE("선택하신 일정에 바로 테이블이 구성되었어요"),
    REALLOCATED_SAME_SLOT("같은 일정의 다른 테이블로 조정되었어요"),
    REALLOCATED_OTHER_REGION("같은 날 선택하신 다른 지역 테이블로 조정되었어요"),
    REALLOCATED_OTHER_DATE("선택하신 다른 날짜 테이블로 조정되었어요"),
    UNDER_MIN_SIZE("해당 일정에 조건이 맞는 신청자가 부족했어요"),
    AGE_GAP("해당 일정에 연령 조건이 맞는 테이블이 없었어요"),
    BLOCKED_PAIR("해당 일정에 함께 배치할 수 있는 테이블이 없었어요"),
    LOW_SCORE("조합 기준을 충분히 만족하는 테이블을 만들지 못했어요"),
    DEFERRED("선택하신 다음 일정에서 다시 매칭해요"),
    NOT_PAID("결제가 완료되지 않아 이번 매칭에서 제외되었어요. 결제 기한 안에 결제해 주세요"),
    ACCOUNT_RESTRICTED("현재 계정 상태로는 매칭에 참여할 수 없어요. 고객센터로 문의해 주세요"),
    DUPLICATE_DATE("같은 날 다른 일정에 이미 배정되어 이 일정은 제외되었어요");

    private final String text;

    ReasonCode(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }

    /** 재배치 단계(1~4)의 사유 코드. */
    public static ReasonCode forReallocStep(int step) {
        return switch (step) {
            case 1 -> REALLOCATED_SAME_SLOT;
            case 2 -> REALLOCATED_OTHER_REGION;
            case 3, 4 -> REALLOCATED_OTHER_DATE;
            default -> throw new IllegalArgumentException("재배치 단계는 1~4: " + step);
        };
    }
}
