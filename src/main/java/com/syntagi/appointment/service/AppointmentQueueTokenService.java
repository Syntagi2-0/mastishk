package com.syntagi.appointment.service;

import com.syntagi.appointment.entity.Appointment;
import com.syntagi.appointment.repository.AppointmentRepository;
import com.syntagi.queue.entity.QueueSession;
import com.syntagi.queue.entity.QueueToken;
import com.syntagi.queue.enums.QueueTokenSourceType;
import com.syntagi.queue.repository.QueueTokenRepository;
import com.syntagi.queue.service.AppointmentQueueTokenCoordinator;
import com.syntagi.queue.service.QueueTimeService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.syntagi.notification.service.NotificationService;

@Service
public class AppointmentQueueTokenService implements AppointmentQueueTokenCoordinator {

    private final AppointmentRepository appointmentRepository;
    private final QueueTokenRepository tokenRepository;
    private final QueueTimeService timeService;
    private final NotificationService notificationService;

    public AppointmentQueueTokenService(
            AppointmentRepository appointmentRepository,
            QueueTokenRepository tokenRepository,
            QueueTimeService timeService,
            NotificationService notificationService) {
        this.appointmentRepository = appointmentRepository;
        this.tokenRepository = tokenRepository;
        this.timeService = timeService;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public int generateFor(QueueSession session) {
        List<Appointment> appointments = appointmentRepository.findConfirmedWithoutQueueToken(
                session.getBusinessService().getId(), session.getBusinessDate());
        for (Appointment appointment : appointments) {
            int number = session.nextAppointmentTokenNumber();
            long queueOrder = session.nextGlobalTokenNumber();
            QueueToken token = tokenRepository.save(new QueueToken(
                    session, session.getBusiness(), session.getBusinessService(),
                    appointment.getCustomer(), appointment, number,
                    "A%03d".formatted(number), QueueTokenSourceType.APPOINTMENT,
                    appointment.getScheduledStartTime(), timeService.nowOffset(),
                    queueOrder, 0, null));
            notificationService.appointmentTokenGenerated(token);
        }
        tokenRepository.flush();
        return appointments.size();
    }
}
