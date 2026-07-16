package com.syntagi.queue.dto.response;

import com.syntagi.queue.enums.QueueTokenStatus;
import java.time.OffsetDateTime;

public record WaitingQueueTokenResponse(
        String token,
        QueueTokenStatus status,
        QueueCustomerResponse customer,
        OffsetDateTime joinedAt,
        int priority) {
}
