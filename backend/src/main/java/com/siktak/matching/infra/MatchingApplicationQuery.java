package com.siktak.matching.infra;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.siktak.application.domain.Application;

/**
 * 매칭 후보 조회 (matching.md 5-1). 신청 도메인 저장소(V1)를 건드리지 않으려고 매칭 쪽에 따로 둔다.
 */
public interface MatchingApplicationQuery extends Repository<Application, Long> {

    /**
     * 대상 회차를 하나 이상 고른 제출 신청 중 매칭 대기({@code WAITING})이거나 결제 전(매칭 상태 없음)인 것.
     * 결제 전 신청은 엔진이 {@code NOT_PAID}로 제외하고 로그만 남긴다.
     */
    @EntityGraph(attributePaths = "slotChoices")
    @Query("""
            select distinct a from Application a join a.slotChoices c
            where c.eventSlotId in :slotIds
              and a.applicationStatus = com.siktak.application.domain.status.ApplicationStatus.SUBMITTED
              and (a.matchStatus = com.siktak.application.domain.status.MatchStatus.WAITING or a.matchStatus is null)
            """)
    List<Application> findCandidates(@Param("slotIds") Collection<Long> slotIds);
}
