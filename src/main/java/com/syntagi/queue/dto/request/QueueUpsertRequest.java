package com.syntagi.queue.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record QueueUpsertRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull UUID serviceId) {
}
