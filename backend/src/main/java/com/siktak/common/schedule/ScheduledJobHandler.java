package com.siktak.common.schedule;

import java.util.List;

/**
 * 시각 기반 작업 종류 하나를 처리한다. 새 작업(REMINDER_D1, CONTENT_POST, EVENT_END, RESOLUTION_TIMEOUT 등)은
 * 이 인터페이스를 구현한 빈을 추가하면 실행기에 자동으로 붙는다.
 * <ul>
 *   <li>{@link #loadPending()}: 기동 시·시드 초기화 시 1회만 호출된다. 아직 실행되지 않은 작업을 DB에서 읽는다.</li>
 *   <li>{@link #execute}: 도래한 작업을 실행한다. 반드시 멱등이어야 한다 (catch-up·재시도·다중 인스턴스).</li>
 * </ul>
 * 작업이 새로 생기거나 바뀌면 {@link ScheduledJobRegistry#schedule}로 등록한다 (DB 폴링 금지).
 */
public interface ScheduledJobHandler {

    String jobType();

    List<ScheduledJob> loadPending();

    void execute(long targetId, JobTrigger trigger);
}
