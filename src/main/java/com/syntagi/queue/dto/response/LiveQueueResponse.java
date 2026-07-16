package com.syntagi.queue.dto.response;

import com.syntagi.queue.enums.QueueTokenStatus;

public record LiveQueueResponse(
        String token,
        QueueTokenStatus status,
        String currentToken,
        long waitingCustomers,
        Integer estimatedPosition,
        long estimatedWaitingCount) {
}
