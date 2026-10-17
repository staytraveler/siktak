package com.siktak.common.schedule;

/**
 * 작업을 실행시킨 계기. triggeredBy가 있으면 운영자·데모 도구가 시계를 앞당겨 실행한 것이다.
 * 상태 이력의 주체는 여전히 SCHEDULER이고, triggeredBy는 actor_id·reason에 남긴다 (states.md actor 표).
 */
public record JobTrigger(Long triggeredBy, String reason) {

    public static JobTrigger tick() {
        return new JobTrigger(null, "정기 실행");
    }

    public static JobTrigger catchUp() {
        return new JobTrigger(null, "기동 시 지난 작업 실행");
    }

    public static JobTrigger byOperator(Long operatorId, String reason) {
        return new JobTrigger(operatorId, reason);
    }
}
