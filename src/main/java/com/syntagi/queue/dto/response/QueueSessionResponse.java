package com.syntagi.queue.dto.response;

import com.syntagi.queue.enums.QueueSessionStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record QueueSessionResponse(
        UUID queueSessionId,
        UUID queueId,
        String queueName,
        UUID serviceId,
        String serviceName,
        LocalDate businessDate,
        QueueSessionStatus status,
        LocalTime openingTime,
        LocalTime closingTime,
        OffsetDateTime openedAt,
        OffsetDateTime closedAt) {
}
