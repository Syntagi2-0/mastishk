package com.syntagi.dashboard.controller;

import com.syntagi.common.api.ApiResponse;
import com.syntagi.dashboard.dto.response.QueueDashboardResponse;
import com.syntagi.dashboard.dto.response.DashboardResponse;
import com.syntagi.dashboard.dto.response.TodayAppointmentsResponse;
import com.syntagi.dashboard.dto.response.TodayQueueResponse;
import com.syntagi.dashboard.service.DashboardService;
import com.syntagi.dashboard.service.QueueDashboardService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class QueueDashboardController {

    private final QueueDashboardService dashboardService;
    private final DashboardService fullDashboardService;

    public QueueDashboardController(
            QueueDashboardService dashboardService, DashboardService fullDashboardService) {
        this.dashboardService = dashboardService;
        this.fullDashboardService = fullDashboardService;
    }

    @GetMapping
    public ApiResponse<DashboardResponse> dashboard() {
        return ApiResponse.success(fullDashboardService.dashboard());
    }

    @GetMapping("/today-queue")
    public ApiResponse<TodayQueueResponse> todayQueue(
            @RequestParam(required = false) UUID serviceId) {
        return ApiResponse.success(fullDashboardService.todayQueue(serviceId));
    }

    @GetMapping("/today-appointments")
    public ApiResponse<TodayAppointmentsResponse> todayAppointments() {
        return ApiResponse.success(fullDashboardService.todayAppointments());
    }

    @GetMapping("/queue")
    public ApiResponse<QueueDashboardResponse> queueDashboard(
            @RequestParam(required = false) UUID serviceId) {
        return ApiResponse.success(dashboardService.getQueueDashboard(serviceId));
    }
}
