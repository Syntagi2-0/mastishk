package com.syntagi.appointment.dto.response;

import com.syntagi.appointment.enums.AppointmentStatus;
import java.time.LocalDate;
import java.time.LocalTime;

public record PublicAppointmentResponse(
        String bookingReference,
        String businessName,
        String serviceName,
        LocalDate appointmentDate,
        LocalTime startTime,
        LocalTime endTime,
        AppointmentStatus status,
        String customerName,
        QueueTokenDetailsResponse queueToken) {
}
