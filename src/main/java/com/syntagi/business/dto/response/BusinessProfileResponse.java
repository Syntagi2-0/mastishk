package com.syntagi.business.dto.response;

import com.syntagi.business.enums.BusinessStatus;
import java.util.UUID;

public record BusinessProfileResponse(
        UUID id,
        String name,
        String slug,
        String businessType,
        String email,
        String mobile,
        String addressLine,
        String city,
        String state,
        String postalCode,
        String countryCode,
        String timezone,
        String publicQueueCode,
        BusinessStatus status) {
}
