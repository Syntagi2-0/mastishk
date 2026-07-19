package com.syntagi.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterOwnerRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @Pattern(regexp = "^\\+?[0-9]{7,20}$", message = "must be a valid mobile number")
                String mobile,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 200) String businessName,
        @NotBlank @Size(max = 50) String businessType,
        @NotBlank
                @Pattern(
                        regexp = "(?i)^[a-z]{2,5}$",
                        message = "must contain between 2 and 5 letters")
                String country,
        @NotBlank @Size(max = 80) String timezone) {
}
