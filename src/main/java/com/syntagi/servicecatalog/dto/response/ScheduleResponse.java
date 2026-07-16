package com.syntagi.servicecatalog.dto.response;

import java.time.LocalTime;
import java.util.UUID;

public record ScheduleResponse(
        UUID id,
        int dayOfWeek,
        LocalTime operatingStartTime,
        LocalTime operatingEndTime,
        int queueOpenBeforeMinutes,
        boolean appointmentBookingEnabled,
        boolean walkInEnabled,
        boolean active) {
}
