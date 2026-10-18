package com.siktak.feedback.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.siktak.application.domain.status.AttendanceStatus;
import com.siktak.feedback.domain.Attendance;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByApplicationId(Long applicationId);

    boolean existsByApplicationId(Long applicationId);

    List<Attendance> findByUserIdOrderByIdDesc(Long userId);

    List<Attendance> findByDiningTableIdOrderByIdAsc(Long diningTableId);

    @Query("""
            select a from Attendance a
            where a.diningTableId in (select t.id from com.siktak.matching.domain.DiningTable t where t.eventSlotId = :slotId)
            order by a.id
            """)
    List<Attendance> findByEventSlotId(@Param("slotId") Long slotId);

    @Query("""
            select a from Attendance a
            where a.attendanceStatus = :status
              and a.diningTableId in (select t.id from com.siktak.matching.domain.DiningTable t where t.eventSlotId = :slotId)
            order by a.id
            """)
    List<Attendance> findByEventSlotIdAndStatus(@Param("slotId") Long slotId,
            @Param("status") AttendanceStatus status);
}
