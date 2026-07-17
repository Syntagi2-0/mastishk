package com.syntagi.dashboard.dto.response;

import com.syntagi.queue.enums.QueueTokenSourceType;
import com.syntagi.queue.enums.QueueTokenStatus;
import java.time.LocalTime;
import java.time.OffsetDateTime;

public record DashboardQueueTokenResponse(
        String tokenDisplay,
        String serviceName,
        String customerName,
        QueueTokenSourceType sourceType,
        QueueTokenStatus status,
        LocalTime scheduledTime,
        OffsetDateTime joinedAt,
        OffsetDateTime calledAt,
        OffsetDateTime completedAt) {}
