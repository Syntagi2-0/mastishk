package com.syntagi.staff.dto.response;

import com.syntagi.auth.enums.BusinessRole;
import com.syntagi.auth.enums.BusinessUserStatus;
import com.syntagi.auth.enums.UserStatus;
import java.util.UUID;

public record StaffMemberResponse(
        UUID businessUserId,
        UUID userId,
        String fullName,
        String email,
        String mobile,
        UserStatus userStatus,
        BusinessRole role,
        BusinessUserStatus status) {
}
