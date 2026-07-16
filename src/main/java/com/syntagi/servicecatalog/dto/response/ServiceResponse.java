package com.syntagi.servicecatalog.dto.response;

import com.syntagi.servicecatalog.enums.ServiceMode;
import java.util.UUID;

public record ServiceResponse(
        UUID id,
        String name,
        String description,
        String serviceCode,
        ServiceMode serviceMode,
        Integer expectedDurationMinutes,
        Integer appointmentSlotDurationMinutes,
        boolean active,
        int displayOrder) {
}
