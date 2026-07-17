package com.syntagi.appointment.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record GenerateSlotsRequest(
        @NotNull UUID serviceId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate) {
}
