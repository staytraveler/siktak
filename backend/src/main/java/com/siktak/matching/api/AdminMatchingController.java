package com.siktak.matching.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.chat.application.ChatService;
import com.siktak.common.security.AuthUser;
import com.siktak.matching.application.MatchingService;
import com.siktak.matching.domain.DiningTable;
import com.siktak.matching.domain.MatchRun;
import com.siktak.matching.domain.TableMember;
import com.siktak.user.domain.User;
import com.siktak.user.infra.UserRepository;

import lombok.RequiredArgsConstructor;

/** 운영자 매칭 실행·결과 조회 (PRD 11장 자동 매칭 모니터의 최소 버전). */
@RestController
@RequestMapping("/api/v1/admin/slots/{slotId}")
@RequiredArgsConstructor
public class AdminMatchingController {

    private final MatchingService matchingService;
    private final ChatService chatService;
    private final UserRepository userRepository;

    /** [2.3] 스케줄러 대신 운영자가 실행한다. 다시 눌러도 같은 실행 결과를 돌려준다 (멱등). */
    @PostMapping("/run-matching")
    public MatchRunResponse run(@AuthenticationPrincipal AuthUser operator, @PathVariable Long slotId) {
        return MatchRunResponse.from(matchingService.runForSlot(slotId, operator.id()));
    }

    @GetMapping("/tables")
    public List<TableResponse> tables(@PathVariable Long slotId) {
        return matchingService.tablesOf(slotId).stream().map(this::toResponse).toList();
    }

    private TableResponse toResponse(DiningTable table) {
        List<TableMember> members = matchingService.membersOf(table.getId());
        Map<Long, User> users = userRepository.findAllById(members.stream().map(TableMember::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Long chatRoomId = chatService.findByTable(table.getId()).map(r -> r.getId()).orElse(null);
        return new TableResponse(table.getId(), table.getTableStatus().name(), table.getMemberCount(),
                table.getConfirmedAt(), chatRoomId, table.getSummary(),
                members.stream().map(m -> new MemberResponse(m.getApplicationId(), m.getUserId(),
                        users.containsKey(m.getUserId()) ? users.get(m.getUserId()).getName() : null)).toList());
    }

    public record MatchRunResponse(Long id, String runKey, String algorithmVersion, String runStatus,
            int candidateCount, Map<String, Object> summary, Instant startedAt, Instant finishedAt) {

        static MatchRunResponse from(MatchRun run) {
            return new MatchRunResponse(run.getId(), run.getRunKey(), run.getAlgorithmVersion(),
                    run.getRunStatus().name(), run.getCandidateCount(), run.getSummary(),
                    run.getStartedAt(), run.getFinishedAt());
        }
    }

    public record TableResponse(Long id, String tableStatus, int memberCount, Instant confirmedAt, Long chatRoomId,
            Map<String, Object> summary, List<MemberResponse> members) {
    }

    public record MemberResponse(Long applicationId, Long userId, String name) {
    }
}
