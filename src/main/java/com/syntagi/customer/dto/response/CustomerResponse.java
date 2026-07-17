package com.syntagi.customer.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerResponse(
        UUID customerId,
        String fullName,
        String mobile,
        String email,
        OffsetDateTime createdAt) {}
