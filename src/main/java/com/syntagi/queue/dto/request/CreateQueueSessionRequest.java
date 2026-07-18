package com.syntagi.queue.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.UUID;

public record CreateQueueSessionRequest(
        @NotNull UUID serviceId,
        LocalTime openingTime,
        LocalTime closingTime) {
}
