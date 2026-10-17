package com.siktak.common.api;

import java.time.Clock;
import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

    private final Clock clock;

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("UP", Instant.now(clock));
    }

    public record HealthResponse(String status, Instant time) {
    }
}
