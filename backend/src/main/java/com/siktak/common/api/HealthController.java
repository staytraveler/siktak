package com.siktak.common.api;

import java.time.Clock;
import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.common.infra.DatabaseHealthChecker;

import lombok.RequiredArgsConstructor;

/**
 * 헬스체크. Neon은 쓸 때만 깨우므로(decisions.md) DB 확인은 별도 경로로 분리한다.
 * <ul>
 *   <li>{@code /api/v1/health}: liveness. DB를 조회하지 않는다. Render Health Check Path.</li>
 *   <li>{@code /api/v1/health/db}: {@code SELECT 1}로 DB 확인. 배포 확인 화면·수동 점검용.
 *       DB가 내려가도 HTTP 200으로 응답하고 db 필드로 상태를 알린다.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

    private final Clock clock;
    private final DatabaseHealthChecker databaseHealthChecker;

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("UP", Instant.now(clock));
    }

    @GetMapping("/db")
    public DbHealthResponse db() {
        String db = databaseHealthChecker.isUp() ? "UP" : "DOWN";
        return new DbHealthResponse(db, Instant.now(clock));
    }

    public record HealthResponse(String status, Instant time) {
    }

    public record DbHealthResponse(String db, Instant time) {
    }
}
