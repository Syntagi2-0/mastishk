package com.syntagi.dashboard.dto.response;

public record DashboardResponse(
        BusinessSummary business,
        QueueSummary queue,
        AppointmentSummary appointments,
        long totalActiveServices,
        long totalActiveStaff) {

    public record BusinessSummary(String name, String businessType) {}

    public record QueueSummary(
            String currentToken,
            String currentCustomer,
            long waitingCount,
            long skippedCount,
            long completedCount,
            long totalTokensToday) {}

    public record AppointmentSummary(
            long totalToday,
            long confirmed,
            long completed,
            long cancelled,
            long noShow) {}
}
