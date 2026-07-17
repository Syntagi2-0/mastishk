package com.syntagi.appointment.dto.response;

import com.syntagi.appointment.enums.AppointmentStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentResponse(
        UUID appointmentId,
        String bookingReference,
        UUID serviceId,
        String serviceName,
        LocalDate appointmentDate,
        LocalTime startTime,
        LocalTime endTime,
        AppointmentStatus status,
        String customerName,
        String mobile,
        String email,
        String customerNotes,
        String cancellationReason,
        QueueTokenDetailsResponse queueToken) {
}
