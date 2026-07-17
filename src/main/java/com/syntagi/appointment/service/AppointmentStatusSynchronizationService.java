package com.syntagi.appointment.service;

import com.syntagi.appointment.entity.Appointment;
import com.syntagi.queue.entity.QueueToken;
import com.syntagi.queue.service.AppointmentStatusCoordinator;
import org.springframework.stereotype.Service;

@Service
public class AppointmentStatusSynchronizationService implements AppointmentStatusCoordinator {

    @Override
    public void completed(QueueToken token) {
        Appointment appointment = token.getAppointment();
        if (appointment != null && appointment.isConfirmed()) {
            appointment.complete();
        }
    }

    @Override
    public void noShow(QueueToken token) {
        Appointment appointment = token.getAppointment();
        if (appointment != null && appointment.isConfirmed()) {
            appointment.markNoShow();
        }
    }
}
