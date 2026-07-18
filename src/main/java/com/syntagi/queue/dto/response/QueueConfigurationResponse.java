package com.syntagi.queue.dto.response;

import com.syntagi.queue.enums.QueueStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record QueueConfigurationResponse(
        UUID id,
        UUID serviceId,
        String serviceName,
        String name,
        QueueStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version) {
}
