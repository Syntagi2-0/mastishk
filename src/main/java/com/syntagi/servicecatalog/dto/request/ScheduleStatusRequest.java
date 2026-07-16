package com.syntagi.servicecatalog.dto.request;

import jakarta.validation.constraints.NotNull;

public record ScheduleStatusRequest(@NotNull Boolean active) {
}
