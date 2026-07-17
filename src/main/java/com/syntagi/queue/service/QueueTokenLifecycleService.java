package com.syntagi.queue.service;

import com.syntagi.queue.entity.QueueToken;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

@Service
public class QueueTokenLifecycleService {

    private final AppointmentStatusCoordinator appointmentStatusCoordinator;
    private final QueueNotificationCoordinator notificationCoordinator;

    public QueueTokenLifecycleService(
            AppointmentStatusCoordinator appointmentStatusCoordinator,
            QueueNotificationCoordinator notificationCoordinator) {
        this.appointmentStatusCoordinator = appointmentStatusCoordinator;
        this.notificationCoordinator = notificationCoordinator;
    }

    public void complete(QueueToken token, OffsetDateTime completedAt) {
        token.complete(completedAt);
        appointmentStatusCoordinator.completed(token);
    }

    public void noShow(QueueToken token) {
        token.markNoShow();
        appointmentStatusCoordinator.noShow(token);
    }

    public void cancel(QueueToken token, OffsetDateTime cancelledAt) {
        token.cancel(cancelledAt);
        notificationCoordinator.tokenCancelled(token);
    }
}
