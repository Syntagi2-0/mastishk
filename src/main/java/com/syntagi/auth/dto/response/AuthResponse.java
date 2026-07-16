package com.syntagi.auth.dto.response;

import com.syntagi.auth.enums.BusinessRole;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        AuthenticatedUserResponse user,
        AuthenticatedBusinessResponse business,
        BusinessRole role) {
}
