package com.syntagi.notification.service;

import com.syntagi.appointment.entity.Appointment;
import com.syntagi.business.entity.Business;
import com.syntagi.common.exception.ApplicationException;
import com.syntagi.common.exception.ErrorCode;
import com.syntagi.customer.entity.Customer;
import com.syntagi.notification.entity.Notification;
import com.syntagi.notification.enums.NotificationChannel;
import com.syntagi.notification.enums.NotificationStatus;
import com.syntagi.notification.enums.NotificationType;
import com.syntagi.notification.repository.NotificationRepository;
import com.syntagi.queue.entity.QueueToken;
import com.syntagi.queue.repository.QueueTokenRepository;
import com.syntagi.queue.service.QueueNotificationCoordinator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService implements QueueNotificationCoordinator {

    private final NotificationRepository repository;
    private final Clock clock;
    private final QueueTokenRepository tokenRepository;

    public NotificationService(
            NotificationRepository repository,
            Clock clock,
            QueueTokenRepository tokenRepository) {
        this.repository = repository;
        this.clock = clock;
        this.tokenRepository = tokenRepository;
    }

    @Transactional
    public Notification appointmentBooked(Appointment appointment) {
        return create(
                appointment.getBusiness(), appointment.getCustomer(), appointment, null,
                NotificationChannel.BROWSER, NotificationType.APPOINTMENT_BOOKED,
                "Appointment booked",
                "Your appointment for %s at %s on %s is confirmed."
                        .formatted(appointment.getBusinessService().getName(),
                                appointment.getScheduledStartTime(), appointment.getAppointmentDate()),
                key(NotificationType.APPOINTMENT_BOOKED, appointment.getId(), null,
                        NotificationChannel.BROWSER));
    }

    @Transactional
    public Notification appointmentCancelled(Appointment appointment) {
        return create(
                appointment.getBusiness(), appointment.getCustomer(), appointment, null,
                NotificationChannel.BROWSER, NotificationType.APPOINTMENT_CANCELLED,
                "Appointment cancelled",
                "Your appointment for %s on %s has been cancelled."
                        .formatted(appointment.getBusinessService().getName(),
                                appointment.getAppointmentDate()),
                key(NotificationType.APPOINTMENT_CANCELLED, appointment.getId(), null,
                        NotificationChannel.BROWSER));
    }

    @Transactional
    public Notification appointmentTokenGenerated(QueueToken token) {
        token = context(token);
        Appointment appointment = token.getAppointment();
        return create(
                token.getBusiness(), token.getCustomer(), appointment, token,
                NotificationChannel.BROWSER, NotificationType.APPOINTMENT_TOKEN_GENERATED,
                "Queue token ready",
                "Booking %s has queue token %s for %s."
                        .formatted(appointment.getBookingReference(), token.getTokenDisplay(),
                                token.getBusinessService().getName()),
                key(NotificationType.APPOINTMENT_TOKEN_GENERATED, token.getId(), null,
                        NotificationChannel.BROWSER));
    }

    @Override
    @Transactional
    public void tokenCalled(QueueToken token) {
        token = context(token);
        create(
                token.getBusiness(), token.getCustomer(), token.getAppointment(), token,
                NotificationChannel.BROWSER, NotificationType.QUEUE_TOKEN_CALLED,
                "It is your turn",
                "%s is now calling token %s for %s."
                        .formatted(token.getBusiness().getName(), token.getTokenDisplay(),
                                token.getBusinessService().getName()),
                key(NotificationType.QUEUE_TOKEN_CALLED, token.getId(), token.getCalledAt(),
                        NotificationChannel.BROWSER));
    }

    @Override
    @Transactional
    public void tokenSkipped(QueueToken token) {
        token = context(token);
        create(
                token.getBusiness(), token.getCustomer(), token.getAppointment(), token,
                NotificationChannel.BROWSER, NotificationType.QUEUE_TOKEN_SKIPPED,
                "Queue token skipped",
                "Token %s for %s was skipped. Please contact %s for assistance."
                        .formatted(token.getTokenDisplay(), token.getBusinessService().getName(),
                                token.getBusiness().getName()),
                key(NotificationType.QUEUE_TOKEN_SKIPPED, token.getId(), token.getSkippedAt(),
                        NotificationChannel.BROWSER));
    }

    @Override
    @Transactional
    public void tokenCancelled(QueueToken token) {
        token = context(token);
        create(
                token.getBusiness(), token.getCustomer(), token.getAppointment(), token,
                NotificationChannel.BROWSER, NotificationType.QUEUE_TOKEN_CANCELLED,
                "Queue token cancelled",
                "Token %s for %s at %s has been cancelled."
                        .formatted(token.getTokenDisplay(), token.getBusinessService().getName(),
                                token.getBusiness().getName()),
                key(NotificationType.QUEUE_TOKEN_CANCELLED, token.getId(), token.getCancelledAt(),
                        NotificationChannel.BROWSER));
    }

    @Transactional
    public Notification createWhatsAppLink(
            Business business,
            Customer customer,
            Appointment appointment,
            QueueToken token,
            NotificationType type,
            String title,
            String message,
            String eventKey) {
        return create(business, customer, appointment, token, NotificationChannel.WHATSAPP_LINK,
                type, title, message, eventKey + ":WHATSAPP_LINK");
    }

    @Transactional
    public Notification markSent(UUID notificationId) {
        Notification notification = locked(notificationId);
        notification.markSent(now());
        return notification;
    }

    @Transactional
    public Notification markFailed(UUID notificationId, String reason) {
        Notification notification = locked(notificationId);
        notification.markFailed(safeFailureReason(reason));
        return notification;
    }

    @Transactional
    public Notification markSkipped(UUID notificationId) {
        Notification notification = locked(notificationId);
        notification.markSkipped();
        return notification;
    }

    private Notification create(
            Business business,
            Customer customer,
            Appointment appointment,
            QueueToken token,
            NotificationChannel channel,
            NotificationType type,
            String title,
            String message,
            String deduplicationKey) {
        return repository.findByDeduplicationKey(deduplicationKey)
                .orElseGet(() -> repository.save(new Notification(
                        business, customer, appointment, token, channel, type,
                        customer == null ? null : customer.getMobile(), title, message,
                        deduplicationKey)));
    }

    private Notification locked(UUID notificationId) {
        return repository.findByIdForUpdate(notificationId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Notification was not found"));
    }

    private QueueToken context(QueueToken token) {
        if (token == null || token.getId() == null) {
            throw new ApplicationException(ErrorCode.QUEUE_TOKEN_NOT_FOUND);
        }
        return tokenRepository.findNotificationContextById(token.getId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.QUEUE_TOKEN_NOT_FOUND));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String key(
            NotificationType type, UUID entityId, OffsetDateTime eventTime,
            NotificationChannel channel) {
        return type + ":" + entityId
                + (eventTime == null ? "" : ":" + eventTime.toInstant())
                + ":" + channel;
    }

    private static String safeFailureReason(String reason) {
        String value = reason == null ? "Notification delivery failed" : reason
                .replaceAll("[\\r\\n\\t]+", " ").trim();
        if (value.isEmpty()) {
            value = "Notification delivery failed";
        }
        return value.substring(0, Math.min(value.length(), 500));
    }
}
