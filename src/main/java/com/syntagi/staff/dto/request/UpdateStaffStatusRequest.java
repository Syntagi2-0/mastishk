package com.syntagi.staff.dto.request;

import com.syntagi.auth.enums.BusinessUserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStaffStatusRequest(@NotNull BusinessUserStatus status) {
}
