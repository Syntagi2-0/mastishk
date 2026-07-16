package com.syntagi.queue.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record WalkInRequest(
        @NotNull UUID serviceId,
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Size(max = 20) String mobile) {
}
