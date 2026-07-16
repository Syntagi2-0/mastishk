package com.syntagi.queue.dto.response;

import com.syntagi.queue.enums.QueueSessionStatus;
import java.util.UUID;

public record QueueCurrentResponse(
        UUID serviceId,
        String serviceName,
        QueueCustomerResponse currentCustomer,
        String currentToken,
        QueueSessionStatus queueStatus,
        long waitingCount) {
}
