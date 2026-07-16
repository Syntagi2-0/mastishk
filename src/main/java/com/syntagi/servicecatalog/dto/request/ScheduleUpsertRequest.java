package com.syntagi.servicecatalog.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record ScheduleUpsertRequest(
        @NotNull @Min(1) @Max(7) Integer dayOfWeek,
        @NotNull LocalTime operatingStartTime,
        @NotNull LocalTime operatingEndTime,
        @NotNull @Min(0) @Max(1440) Integer queueOpenBeforeMinutes,
        @NotNull Boolean appointmentBookingEnabled,
        @NotNull Boolean walkInEnabled) {
}
