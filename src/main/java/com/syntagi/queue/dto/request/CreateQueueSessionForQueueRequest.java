package com.syntagi.queue.dto.request;

import java.time.LocalTime;

public record CreateQueueSessionForQueueRequest(
        LocalTime openingTime,
        LocalTime closingTime) {
}
