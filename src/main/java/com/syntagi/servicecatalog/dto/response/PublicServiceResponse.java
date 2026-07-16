package com.syntagi.servicecatalog.dto.response;

import com.syntagi.servicecatalog.enums.ServiceMode;
import java.util.UUID;

public record PublicServiceResponse(
        UUID serviceId,
        String name,
        String description,
        ServiceMode serviceMode,
        Integer expectedDurationMinutes,
        boolean supportsWalkIn,
        boolean supportsAppointment) {
}
