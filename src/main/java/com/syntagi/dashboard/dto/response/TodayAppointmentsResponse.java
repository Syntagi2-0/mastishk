package com.syntagi.dashboard.dto.response;

import com.syntagi.appointment.dto.response.AppointmentResponse;
import java.util.List;
import java.util.UUID;

public record TodayAppointmentsResponse(List<ServiceAppointments> services) {

    public record ServiceAppointments(
            UUID serviceId,
            String serviceName,
            List<AppointmentResponse> appointments) {}
}
