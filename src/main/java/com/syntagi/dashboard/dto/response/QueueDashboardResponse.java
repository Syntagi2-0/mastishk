package com.syntagi.dashboard.dto.response;

import com.syntagi.queue.enums.QueueSessionStatus;
import java.util.UUID;

public record QueueDashboardResponse(
        UUID serviceId,
        String serviceName,
        QueueSessionStatus queueStatus,
        String currentToken,
        long waitingCount,
        long completedCount,
        long skippedCount) {
}
