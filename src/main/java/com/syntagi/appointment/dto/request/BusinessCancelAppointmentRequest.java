package com.syntagi.appointment.dto.request;

import jakarta.validation.constraints.Size;

public record BusinessCancelAppointmentRequest(@Size(max = 500) String reason) {
}
