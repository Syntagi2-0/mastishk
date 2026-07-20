package com.syntagi.queue.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public record WalkInRequest(
        @NotNull @Schema(example = "8ad82f9e-153d-4f15-a103-c635ec1fffc3") UUID serviceId,
        @NotBlank @Size(max = 150) @Schema(example = "Ananya Sharma") String fullName,
        @Size(max = 20)
                @Pattern(
                        regexp = "^$|^\\+?[0-9]{7,20}$",
                        message = "must be a valid mobile number")
                @Schema(example = "9876543210")
                String mobile,
        @jakarta.validation.constraints.Email @Size(max = 255) String email,
        @Size(max = 30) String gender,
        @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(130) Integer age,
        LocalDate dateOfBirth,
        @Size(max = 500) String address,
        @Size(max = 500) String notes) {

    public WalkInRequest(UUID serviceId, String fullName, String mobile) {
        this(serviceId, fullName, mobile, null, null, null, null, null, null);
    }
}
