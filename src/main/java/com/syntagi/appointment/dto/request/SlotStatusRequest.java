package com.syntagi.appointment.dto.request;

import com.syntagi.appointment.enums.AppointmentSlotStatus;
import jakarta.validation.constraints.NotNull;

public record SlotStatusRequest(@NotNull AppointmentSlotStatus status) {
}
