package com.siktak.application.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.siktak.application.domain.Application;
import com.siktak.application.domain.status.MatchStatus;

/** open-in-view가 꺼져 있으므로 응답에 쓰는 slotChoices는 EntityGraph로 함께 읽는다. */
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    @Override
    @EntityGraph(attributePaths = "slotChoices")
    Optional<Application> findById(Long id);

    @EntityGraph(attributePaths = "slotChoices")
    List<Application> findByUserIdOrderByIdDesc(Long userId);

    /** 회차를 후보로 고른 신청 중 주어진 매칭 상태인 것 (신청 순). */
    @Query("""
            select distinct a from Application a join a.slotChoices c
            where c.eventSlotId = :slotId and a.matchStatus = :matchStatus
            order by a.submittedAt asc, a.id asc
            """)
    List<Application> findCandidatesForSlot(@Param("slotId") Long slotId, @Param("matchStatus") MatchStatus matchStatus);
}
