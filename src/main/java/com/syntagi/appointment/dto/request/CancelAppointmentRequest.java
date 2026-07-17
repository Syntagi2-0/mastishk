package com.syntagi.appointment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CancelAppointmentRequest(
        @NotBlank
                @Pattern(
                        regexp = "^\\+?[0-9]{7,20}$",
                        message = "must be a valid mobile number")
                String mobile,
        @Size(max = 500) String reason) {
}
