package com.siktak.safety.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.application.application.ApplicationService;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.exception.application.ExceptionCaseService;
import com.siktak.exception.domain.CaseType;
import com.siktak.matching.domain.TableMember;
import com.siktak.matching.infra.TableMemberRepository;
import com.siktak.safety.domain.BlockSource;
import com.siktak.safety.domain.ReportReason;
import com.siktak.safety.domain.ReportStatus;
import com.siktak.safety.domain.ResolveAction;
import com.siktak.safety.domain.SafetyReport;
import com.siktak.safety.domain.SanctionType;
import com.siktak.safety.domain.UserBlock;
import com.siktak.safety.domain.UserSanction;
import com.siktak.safety.infra.SafetyReportRepository;
import com.siktak.safety.infra.UserBlockRepository;
import com.siktak.safety.infra.UserSanctionRepository;
import com.siktak.user.domain.User;
import com.siktak.user.infra.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 차단·신고와 운영자 조치 (SAF-01·02, PRD 10장·15장).
 * 대상은 같은 테이블 동석자만이고 table_members.id({@code companionId})로 가리킨다 — 다른 참가자의 사용자 ID는 내보내지 않는다.
 * 차단·신고 관계는 상대에게 절대 노출하지 않는다. 신고는 접수 즉시 분리(user_blocks REPORT)되어 다음 매칭에서 H6로 걸러진다.
 */
@Service
@RequiredArgsConstructor
public class SafetyService {

    private final ApplicationService applicationService;
    private final TableMemberRepository tableMemberRepository;
    private final UserBlockRepository blockRepository;
    private final SafetyReportRepository reportRepository;
    private final UserSanctionRepository sanctionRepository;
    private final UserRepository userRepository;
    private final ExceptionCaseService exceptionCaseService;
    private final Clock clock;

    /** 같은 테이블 동석자를 차단한다. 이미 차단(또는 신고로 분리)돼 있으면 그대로 둔다 (멱등). */
    @Transactional
    public void block(Long userId, Long applicationId, Long companionId) {
        Target target = target(userId, applicationId, companionId);
        ensureBlock(userId, target.userId(), BlockSource.USER_BLOCK);
    }

    /**
     * 같은 테이블 동석자를 신고한다 (SAF-01). 같은 테이블의 같은 사람은 한 번만 접수되고 다시 보내면 기존 신고를 돌려준다.
     * 접수 즉시 분리(user_blocks REPORT)하고 운영자 예외 처리함(SAFETY_REPORT)에 올린다.
     */
    @Transactional
    public SafetyReport report(Long userId, Long applicationId, Long companionId, ReportReason reason,
            String description) {
        Target target = target(userId, applicationId, companionId);
        SafetyReport existing = reportRepository
                .findByReporterIdAndReportedIdAndDiningTableId(userId, target.userId(), target.tableId()).orElse(null);
        if (existing != null) {
            return existing;
        }
        SafetyReport report = reportRepository
                .save(SafetyReport.receive(userId, target.userId(), target.tableId(), reason, description));
        ensureBlock(userId, target.userId(), BlockSource.REPORT);
        Map<String, Object> detail = new HashMap<>();
        detail.put("reportId", report.getId());
        detail.put("reasonType", reason.name());
        exceptionCaseService.open(CaseType.SAFETY_REPORT, reportCaseKey(report.getId()), applicationId,
                target.tableId(), "안전 신고 접수 (" + reason.name() + ")", detail);
        return report;
    }

    public record ReportView(SafetyReport report, String reporterName, String reportedName) {
    }

    /** {@code open}이 true면 아직 처리되지 않은 신고(RECEIVED·REVIEWING)만. */
    @Transactional(readOnly = true)
    public List<ReportView> listReports(boolean open) {
        List<SafetyReport> reports = open
                ? reportRepository.findByReportStatusInOrderByIdDesc(
                        List.of(ReportStatus.RECEIVED, ReportStatus.REVIEWING))
                : reportRepository.findAllByOrderByIdDesc();
        Map<Long, User> users = userRepository.findAllById(reports.stream()
                .flatMap(r -> java.util.stream.Stream.of(r.getReporterId(), r.getReportedId())).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return reports.stream().map(r -> new ReportView(r, users.get(r.getReporterId()).getName(),
                users.get(r.getReportedId()).getName())).toList();
    }

    @Transactional(readOnly = true)
    public ReportView view(Long reportId) {
        SafetyReport r = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "신고를 찾을 수 없습니다."));
        return new ReportView(r, userRepository.findById(r.getReporterId()).orElseThrow().getName(),
                userRepository.findById(r.getReportedId()).orElseThrow().getName());
    }

    /**
     * 운영자 조치 (SAF-02). DISMISS는 기각, 그 외는 경고·참가 제한·영구 정지로 {@code user_sanctions}에 기록한다.
     * RESTRICTION은 {@code days}(1 이상) 필수, 계정은 RESTRICTED로, PERMANENT_BAN은 BANNED로 바뀐다.
     * 이미 처리된 신고에 다시 호출하면 아무것도 바꾸지 않는다 (멱등).
     * 제한 기간이 끝나도 자동 해제되지 않는다 — 이번 범위 밖 (7.2 지시서).
     */
    @Transactional
    public SafetyReport resolve(Long operatorId, Long reportId, ResolveAction action, Integer days, String note) {
        SafetyReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "신고를 찾을 수 없습니다."));
        if (report.isClosed()) {
            return report;
        }
        if (action == ResolveAction.RESTRICTION && (days == null || days < 1)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "참가 제한은 기간(일)이 필요합니다.");
        }
        Instant now = Instant.now(clock);
        if (action != ResolveAction.DISMISS) {
            User reported = userRepository.findById(report.getReportedId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "대상 사용자를 찾을 수 없습니다."));
            SanctionType type = switch (action) {
                case WARNING -> SanctionType.WARNING;
                case RESTRICTION -> SanctionType.RESTRICTION;
                default -> SanctionType.PERMANENT_BAN;
            };
            Instant endsAt = type == SanctionType.RESTRICTION ? now.plus(Duration.ofDays(days)) : null;
            sanctionRepository.save(UserSanction.of(reported.getId(), type, note, report.getId(), now, endsAt, operatorId));
            if (type == SanctionType.RESTRICTION) {
                reported.restrict();
            } else if (type == SanctionType.PERMANENT_BAN) {
                reported.ban();
            }
        }
        report.close(action == ResolveAction.DISMISS ? ReportStatus.DISMISSED : ReportStatus.RESOLVED, operatorId, now);
        exceptionCaseService.resolve(reportCaseKey(reportId), operatorId, action.name() + (note == null ? "" : ": " + note));
        return report;
    }

    public static String reportCaseKey(Long reportId) {
        return "SAFETY_REPORT:report-" + reportId;
    }

    private void ensureBlock(Long blockerId, Long blockedId, BlockSource source) {
        UserBlock existing = blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId).orElse(null);
        if (existing == null) {
            blockRepository.save(UserBlock.of(blockerId, blockedId, source));
        }
    }

    private record Target(Long userId, Long tableId) {
    }

    /** 본인 신청의 테이블 동석자만 대상이 된다. 아니면 400 (남의 테이블 사람인지 여부를 알리지 않는다). */
    private Target target(Long userId, Long applicationId, Long companionId) {
        applicationService.getOwned(userId, applicationId);
        TableMember own = tableMemberRepository.findByApplicationIdAndActiveTrue(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "확정된 테이블이 없습니다."));
        TableMember other = tableMemberRepository.findById(companionId)
                .filter(TableMember::isActive)
                .filter(m -> m.getDiningTableId().equals(own.getDiningTableId()))
                .filter(m -> !m.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "같은 테이블 동석자만 선택할 수 있습니다."));
        return new Target(other.getUserId(), own.getDiningTableId());
    }
}
