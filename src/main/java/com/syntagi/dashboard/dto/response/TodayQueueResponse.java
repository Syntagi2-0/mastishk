package com.syntagi.dashboard.dto.response;

import java.util.List;

public record TodayQueueResponse(
        List<DashboardQueueTokenResponse> current,
        List<DashboardQueueTokenResponse> waiting,
        List<DashboardQueueTokenResponse> skipped,
        List<DashboardQueueTokenResponse> completed) {}
