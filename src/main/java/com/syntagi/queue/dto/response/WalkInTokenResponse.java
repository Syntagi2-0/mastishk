package com.syntagi.queue.dto.response;

import com.syntagi.queue.enums.QueueTokenStatus;
import java.time.OffsetDateTime;

public record WalkInTokenResponse(
        String token,
        QueueTokenStatus status,
        String businessName,
        String serviceName,
        OffsetDateTime joinedAt,
        int estimatedPosition,
        long estimatedWaitingCount) {
}
