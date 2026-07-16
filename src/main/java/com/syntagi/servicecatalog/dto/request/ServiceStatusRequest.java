package com.syntagi.servicecatalog.dto.request;

import jakarta.validation.constraints.NotNull;

public record ServiceStatusRequest(@NotNull Boolean active) {
}
