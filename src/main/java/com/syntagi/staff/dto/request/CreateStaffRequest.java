package com.syntagi.staff.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateStaffRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @Pattern(regexp = "^\\+?[0-9]{7,20}$", message = "must be a valid mobile number")
                String mobile,
        @NotBlank @Size(min = 8, max = 72) String temporaryPassword) {

    public CreateStaffRequest {
        fullName = trim(fullName);
        email = trim(email);
        mobile = trimToNull(mobile);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }
}
