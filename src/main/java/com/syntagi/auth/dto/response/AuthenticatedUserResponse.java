package com.syntagi.auth.dto.response;

import com.syntagi.auth.enums.UserStatus;
import java.util.UUID;

public record AuthenticatedUserResponse(
        UUID id,
        String fullName,
        String email,
        String mobile,
        UserStatus status) {
}
