package com.syntagi.auth.dto.response;

import com.syntagi.business.enums.BusinessStatus;
import java.util.UUID;

public record AuthenticatedBusinessResponse(
        UUID id,
        String name,
        String slug,
        String businessType,
        String publicQueueCode,
        BusinessStatus status) {
}
