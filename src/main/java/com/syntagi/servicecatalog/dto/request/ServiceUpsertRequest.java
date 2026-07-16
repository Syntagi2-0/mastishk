package com.syntagi.servicecatalog.dto.request;

import com.syntagi.servicecatalog.enums.ServiceMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ServiceUpsertRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 50) String serviceCode,
        @NotNull ServiceMode serviceMode,
        @NotNull @Positive Integer expectedDurationMinutes,
        @Positive Integer appointmentSlotDurationMinutes,
        @NotNull Integer displayOrder) {

    public ServiceUpsertRequest {
        name = trim(name);
        description = trimToNull(description);
        serviceCode = trim(serviceCode);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }
}
