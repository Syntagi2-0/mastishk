package com.syntagi.dashboard.controller;

import com.syntagi.common.api.ApiResponse;
import com.syntagi.dashboard.dto.response.QueueDashboardResponse;
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

    public QueueDashboardController(QueueDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/queue")
    public ApiResponse<QueueDashboardResponse> queueDashboard(
            @RequestParam(required = false) UUID serviceId) {
        return ApiResponse.success(dashboardService.getQueueDashboard(serviceId));
    }
}
