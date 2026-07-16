package com.syntagi.staff.dto.response;

import com.syntagi.auth.enums.BusinessRole;
import com.syntagi.auth.enums.BusinessUserStatus;
import java.util.UUID;

public record StaffMembershipResponse(
        UUID businessUserId,
        UUID userId,
        UUID businessId,
        String businessName,
        BusinessRole role,
        BusinessUserStatus status) {
}
