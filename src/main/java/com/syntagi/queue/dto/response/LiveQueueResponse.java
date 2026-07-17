package com.syntagi.queue.dto.response;

import com.syntagi.queue.enums.QueueSessionStatus;
import com.syntagi.queue.enums.QueueTokenStatus;

public record LiveQueueResponse(
        String business,
        String service,
        String currentToken,
        String customerToken,
        QueueTokenStatus customerStatus,
        long customersAhead,
        long estimatedWaitingCount,
        long estimatedWaitingTimeMinutes,
        QueueSessionStatus queueStatus,
        String token,
        QueueTokenStatus status,
        long waitingCustomers,
        Integer estimatedPosition) {
}
