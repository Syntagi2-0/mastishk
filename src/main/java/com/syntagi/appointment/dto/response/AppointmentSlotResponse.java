package com.syntagi.appointment.dto.response;

import com.syntagi.appointment.enums.AppointmentSlotStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentSlotResponse(
        UUID slotId,
        UUID serviceId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        int capacity,
        int bookedCount,
        int remainingCapacity,
        AppointmentSlotStatus status) {
}
