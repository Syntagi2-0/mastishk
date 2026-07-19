package com.syntagi.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterOwnerRequest(
        @NotBlank @Size(max = 150) String ownerName,
        @NotBlank @Size(max = 200) String businessName,
        @NotBlank
                @Pattern(
                        regexp = "^(?:\\+?[0-9]{7,20}|[^\\s@]+@[^\\s@]+\\.[^\\s@]+)$",
                        message = "must be a valid mobile number or email address")
                @Size(max = 255)
                String mobileOrEmail,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 80) String timezone) {
}
