package com.syntagi.business.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateBusinessRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 50) String businessType,
        @Email @Size(max = 255) String email,
        @Pattern(regexp = "^$|^\\+?[0-9]{7,20}$", message = "must be a valid mobile number")
                String mobile,
        @Size(max = 500) String addressLine,
        @Size(max = 100) String city,
        @Size(max = 100) String state,
        @Size(max = 20) String postalCode,
        @NotBlank
                @Pattern(
                        regexp = "(?i)^[a-z]{2,5}$",
                        message = "must contain between 2 and 5 letters")
                String countryCode,
        @NotBlank @Size(max = 80) String timezone) {

    public UpdateBusinessRequest {
        name = trim(name);
        businessType = trim(businessType);
        email = trimToNull(email);
        mobile = trimToNull(mobile);
        addressLine = trimToNull(addressLine);
        city = trimToNull(city);
        state = trimToNull(state);
        postalCode = trimToNull(postalCode);
        countryCode = trim(countryCode);
        timezone = trim(timezone);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }
}
