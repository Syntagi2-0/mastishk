package com.syntagi.auth.dto.response;

import com.syntagi.auth.enums.BusinessRole;

public record CurrentUserResponse(
        AuthenticatedUserResponse user,
        AuthenticatedBusinessResponse business,
        BusinessRole role) {
}
