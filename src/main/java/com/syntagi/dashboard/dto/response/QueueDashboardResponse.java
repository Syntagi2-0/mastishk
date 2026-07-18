package com.syntagi.dashboard.dto.response;

import com.syntagi.queue.enums.QueueSessionStatus;
import com.syntagi.queue.enums.QueueStatus;
import java.util.UUID;

public record QueueDashboardResponse(
        UUID queueSessionId,
        UUID queueId,
        String queueName,
        QueueStatus queueConfigurationStatus,
        UUID serviceId,
        String serviceName,
        QueueSessionStatus queueStatus,
        String currentToken,
        long waitingCount,
        long completedCount,
        long skippedCount) {
}
