package com.siktak.chat.application;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.siktak.matching.domain.TableGuidanceRequested;

import lombok.RequiredArgsConstructor;

/** 매칭·식당 배정 쪽이 보낸 이벤트를 안내 자동화로 잇는다. 확정과 같은 트랜잭션에서 동기로 처리한다. */
@Component
@RequiredArgsConstructor
public class TableGuidanceListener {

    private final TableGuidanceService guidanceService;

    @EventListener
    public void on(TableGuidanceRequested event) {
        guidanceService.announce(event.tableId());
    }
}
