package com.syntagi.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record LoginRequest(
        @NotBlank @Email @Size(max = 255) @Schema(example = "owner@example.com") String email,
        @NotBlank @Size(max = 72) @Schema(example = "StrongPassword123") String password) {
}
