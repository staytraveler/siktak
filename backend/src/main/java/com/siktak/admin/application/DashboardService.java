package com.siktak.admin.application;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.exception.domain.CaseType;

import lombok.RequiredArgsConstructor;

/**
 * 운영 대시보드 집계 (PRD 11장 "바로 보여야 하는 정보", 3장 핵심 지표). 읽기 전용 집계라 SQL로 한 번에 센다.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final String REFUND_STATES = "('REFUND_REQUESTED','REFUND_PROCESSING','REFUNDED','REFUND_FAILED')";

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Dashboard load() {
        long submitted = count("select count(*) from applications where application_status = 'SUBMITTED'");
        long applicants = count(
                "select count(distinct user_id) from applications where application_status = 'SUBMITTED'");
        long paid = count("select count(*) from applications where payment_status = 'PAID'");
        long confirmed = count("select count(*) from applications where match_status = 'CONFIRMED'");
        long waitingChoice = count("select count(*) from applications where match_status = 'ALTERNATIVE_OFFERED'");
        long refunded = count("select count(*) from applications where payment_status = 'REFUNDED'");

        // 회차별 현황. 신청·결제·대체 대기·환불은 후보로 고른 회차 기준, 확정은 배정 회차 기준.
        List<SlotStat> slots = jdbc.query("""
                select s.id, r.name as region_name, s.starts_at, s.slot_status,
                  count(distinct a.id) filter (where a.application_status = 'SUBMITTED') as applied,
                  count(distinct a.id) filter (where a.payment_status = 'PAID') as paid,
                  count(distinct a.id) filter (where a.match_status = 'CONFIRMED' and a.assigned_slot_id = s.id) as confirmed,
                  count(distinct a.id) filter (where a.match_status = 'ALTERNATIVE_OFFERED') as waiting,
                  count(distinct a.id) filter (where a.payment_status in %s) as refunds
                from event_slots s
                join regions r on r.id = s.region_id
                left join application_slot_choices c on c.event_slot_id = s.id
                left join applications a on a.id = c.application_id
                group by s.id, r.name, s.starts_at, s.slot_status
                order by s.starts_at, s.id
                """.formatted(REFUND_STATES),
                (rs, i) -> new SlotStat(rs.getLong("id"), rs.getString("region_name"),
                        rs.getTimestamp("starts_at").toInstant(), rs.getString("slot_status"), rs.getLong("applied"),
                        rs.getLong("paid"), rs.getLong("confirmed"), rs.getLong("waiting"), rs.getLong("refunds")));

        // 자동 확정률 (PRD 3장): 확정된 테이블 중 운영자가 상태를 바꾼 적 없는 테이블.
        // 확정 뒤에 생기는 예외(노쇼 후보·식당 부족 등)는 "확정"에 대한 개입이 아니므로 세지 않는다.
        long tables = count("select count(*) from dining_tables where table_status <> 'DISSOLVED'");
        long autoTables = count("""
                select count(*) from dining_tables t
                where t.table_status <> 'DISSOLVED'
                  and not exists (select 1 from status_histories h
                                  where h.target_type = 'TABLE' and h.target_id = t.id and h.actor_type = 'OPERATOR')
                """);
        // 자동 재배치 성공률: 최초 조합에서 못 배치된 신청(재배치·보류·실패 기록이 있는 신청) 중 재배치로 배치된 신청
        long firstFailed = count("""
                select count(distinct application_id) from match_placement_logs
                where decision in ('REALLOCATED','DEFERRED','FAILED')
                """);
        long reallocated = count("""
                select count(distinct application_id) from match_placement_logs where decision = 'REALLOCATED'
                """);
        // 자동 환불 완료율: 환불 선택 건 중 환불 실패 예외 없이 환불 완료된 건
        long refundChosen = count("select count(*) from applications where payment_status in " + REFUND_STATES);
        long autoRefunded = count("""
                select count(*) from applications a
                where a.payment_status = 'REFUNDED'
                  and not exists (select 1 from exception_cases e
                                  where e.application_id = a.id and e.case_type = 'REFUND_FAILED')
                """);
        long exceptionTotal = count("select count(*) from exception_cases");
        long operatorHandled = count("select count(*) from exception_cases where resolved_by is not null");

        EnumMap<CaseType, Long> open = new EnumMap<>(CaseType.class);
        for (CaseType t : CaseType.values()) {
            open.put(t, 0L);
        }
        jdbc.query("select case_type, count(*) as n from exception_cases where case_status <> 'RESOLVED' group by case_type",
                rs -> {
                    open.put(CaseType.valueOf(rs.getString("case_type")), rs.getLong("n"));
                });
        long openTotal = open.values().stream().mapToLong(Long::longValue).sum();

        return new Dashboard(new Totals(applicants, submitted, paid, confirmed, waitingChoice, refunded), slots,
                new Metrics(rate(autoTables, tables), rate(reallocated, firstFailed), rate(autoRefunded, refundChosen),
                        rate(operatorHandled, submitted), rate(exceptionTotal, submitted), rate(confirmed, paid)),
                new OpenExceptions(openTotal, open));
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    private static Rate rate(long numerator, long denominator) {
        return new Rate(numerator, denominator, denominator == 0 ? null : (double) numerator / denominator);
    }

    public record Dashboard(Totals totals, List<SlotStat> slots, Metrics metrics, OpenExceptions exceptions) {
    }

    /** @param applicants 신청한 고유 사용자, applications 제출된 신청 건 */
    public record Totals(long applicants, long applications, long paid, long confirmed, long waitingChoice,
            long refunded) {
    }

    public record SlotStat(Long slotId, String regionName, Instant startsAt, String slotStatus, long applied,
            long paid, long confirmed, long waiting, long refunds) {
    }

    /** 값은 0~1 비율, 분모가 0이면 {@code rate}는 null. */
    public record Rate(long numerator, long denominator, Double rate) {
    }

    /**
     * @param autoConfirm 해산되지 않은 테이블 중 예외 없이 확정된 비율
     * @param autoRealloc 최초 조합 실패자 중 재배치로 배치된 비율
     * @param autoRefund 환불 선택 건 중 환불 실패 예외 없이 완료된 비율
     * @param operatorIntervention 전체 신청 중 운영자가 직접 처리한 예외 건 비율
     * @param exceptionRate 전체 신청 중 예외 발생 건 비율 (처리 여부 무관)
     * @param matchSuccess 결제 완료 신청 중 확정된 비율
     */
    public record Metrics(Rate autoConfirm, Rate autoRealloc, Rate autoRefund, Rate operatorIntervention,
            Rate exceptionRate, Rate matchSuccess) {
    }

    /** 처리되지 않은(OPEN·IN_PROGRESS) 예외 수. */
    public record OpenExceptions(long total, Map<CaseType, Long> byType) {
    }
}
