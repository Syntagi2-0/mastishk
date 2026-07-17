package com.syntagi.appointment.service;

import com.syntagi.appointment.dto.response.*;
import com.syntagi.appointment.entity.Appointment;
import com.syntagi.appointment.entity.AppointmentSlot;
import com.syntagi.queue.entity.QueueToken;
import com.syntagi.queue.repository.QueueTokenRepository;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AppointmentResponseMapper {

    private final QueueTokenRepository tokenRepository;

    public AppointmentResponseMapper(QueueTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    public AppointmentSlotResponse slot(AppointmentSlot slot) {
        return new AppointmentSlotResponse(
                slot.getId(), slot.getBusinessService().getId(), slot.getSlotDate(),
                slot.getStartTime(), slot.getEndTime(), slot.getCapacity(), slot.getBookedCount(),
                slot.getCapacity() - slot.getBookedCount(), slot.getStatus());
    }

    public PublicAppointmentResponse publicAppointment(Appointment appointment) {
        return new PublicAppointmentResponse(
                appointment.getBookingReference(), appointment.getBusiness().getName(),
                appointment.getBusinessService().getName(), appointment.getAppointmentDate(),
                appointment.getScheduledStartTime(), appointment.getScheduledEndTime(),
                appointment.getStatus(), appointment.getCustomer().getFullName(), token(appointment));
    }

    public AppointmentResponse businessAppointment(Appointment appointment) {
        return businessAppointment(appointment, token(appointment));
    }

    public List<AppointmentResponse> businessAppointments(List<Appointment> appointments) {
        if (appointments.isEmpty()) {
            return List.of();
        }
        Map<java.util.UUID, QueueToken> tokens = tokenRepository
                .findByAppointmentIdIn(appointments.stream().map(Appointment::getId).toList())
                .stream()
                .collect(Collectors.toMap(
                        queueToken -> queueToken.getAppointment().getId(), Function.identity()));
        return appointments.stream()
                .map(appointment -> businessAppointment(
                        appointment,
                        tokens.containsKey(appointment.getId())
                                ? token(tokens.get(appointment.getId())) : null))
                .toList();
    }

    private AppointmentResponse businessAppointment(
            Appointment appointment, QueueTokenDetailsResponse queueToken) {
        return new AppointmentResponse(
                appointment.getId(), appointment.getBookingReference(),
                appointment.getBusinessService().getId(), appointment.getBusinessService().getName(),
                appointment.getAppointmentDate(), appointment.getScheduledStartTime(),
                appointment.getScheduledEndTime(), appointment.getStatus(),
                appointment.getCustomer().getFullName(), appointment.getCustomer().getMobile(),
                appointment.getCustomer().getEmail(), appointment.getCustomerNotes(),
                appointment.getCancellationReason(), queueToken);
    }

    private QueueTokenDetailsResponse token(Appointment appointment) {
        return tokenRepository.findByAppointmentId(appointment.getId())
                .map(this::token)
                .orElse(null);
    }

    private QueueTokenDetailsResponse token(QueueToken token) {
        return new QueueTokenDetailsResponse(token.getTokenDisplay(), token.getStatus());
    }
}
