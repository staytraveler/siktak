package com.siktak.admin.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.admin.application.DashboardService;

import lombok.RequiredArgsConstructor;

/** 운영 대시보드 (PRD 11장). 권한은 SecurityConfig의 {@code /api/v1/admin/**} OPERATOR 규칙. */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public DashboardService.Dashboard get() {
        return dashboardService.load();
    }
}
