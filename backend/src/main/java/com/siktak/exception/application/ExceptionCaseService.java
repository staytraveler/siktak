package com.siktak.exception.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.exception.domain.CaseStatus;
import com.siktak.exception.domain.CaseType;
import com.siktak.exception.domain.ExceptionCase;
import com.siktak.exception.infra.ExceptionCaseRepository;

import lombok.RequiredArgsConstructor;

/**
 * 운영자 예외 처리함 (PRD 11장, 15장). 자동 처리 실패는 로그로 끝내지 않고 여기에 남긴다 (CLAUDE.md 6장).
 */
@Service
@RequiredArgsConstructor
public class ExceptionCaseService {

    private final ExceptionCaseRepository repository;
    private final Clock clock;

    /** 같은 {@code caseKey}가 있으면 새로 만들지 않고 기존 것을 돌려준다 (멱등). */
    @Transactional(propagation = Propagation.MANDATORY)
    public ExceptionCase open(CaseType type, String caseKey, Long applicationId, Long diningTableId, String reason,
            Map<String, Object> detail) {
        return repository.findByCaseKey(caseKey)
                .orElseGet(() -> repository.save(
                        ExceptionCase.open(type, caseKey, applicationId, diningTableId, reason, detail)));
    }

    /** 자동 재시도로 해결됐거나 운영자가 처리했을 때. 없거나 이미 해결됐으면 그대로 둔다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ExceptionCase> resolve(String caseKey, Long operatorId, String note) {
        Optional<ExceptionCase> found = repository.findByCaseKey(caseKey);
        found.ifPresent(c -> c.resolve(operatorId, note, Instant.now(clock)));
        return found;
    }

    @Transactional(readOnly = true)
    public List<ExceptionCase> list(CaseStatus status) {
        return status == null ? repository.findAllByOrderByIdAsc() : repository.findByCaseStatusOrderByIdAsc(status);
    }

    /** 식당 미배정 예외 키 (테이블당 1개). */
    public static String venueUnavailableKey(Long diningTableId) {
        return "VENUE_UNAVAILABLE:table-" + diningTableId;
    }
}
