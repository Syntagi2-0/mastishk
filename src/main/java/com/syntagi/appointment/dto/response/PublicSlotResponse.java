package com.syntagi.appointment.dto.response;

import java.time.LocalTime;

public record PublicSlotResponse(
        String slotId,
        LocalTime startTime,
        LocalTime endTime,
        int remainingCapacity) {
}
