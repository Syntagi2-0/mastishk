package com.syntagi.notification.entity;

import com.syntagi.appointment.entity.Appointment;
import com.syntagi.business.entity.Business;
import com.syntagi.common.exception.InvalidEntityStateException;
import com.syntagi.common.persistence.BaseEntity;
import com.syntagi.customer.entity.Customer;
import com.syntagi.notification.enums.NotificationChannel;
import com.syntagi.notification.enums.NotificationStatus;
import com.syntagi.notification.enums.NotificationType;
import com.syntagi.queue.entity.QueueToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "queue_token_id")
    private QueueToken queueToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    @Column(length = 255)
    private String recipient;

    @Column(length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    public Notification(
            Business business,
            Customer customer,
            Appointment appointment,
            QueueToken queueToken,
            NotificationChannel channel,
            NotificationType notificationType,
            String recipient,
            String title,
            String message) {
        this.business = Objects.requireNonNull(business, "business is required");
        this.customer = customer;
        this.appointment = appointment;
        this.queueToken = queueToken;
        this.channel = Objects.requireNonNull(channel, "channel is required");
        this.notificationType = Objects.requireNonNull(
                notificationType, "notificationType is required");
        this.recipient = trimToNull(recipient);
        this.title = trimToNull(title);
        this.message = requireText(message, "message");
    }

    public void markSent() {
        ensurePending();
        status = NotificationStatus.SENT;
        sentAt = OffsetDateTime.now(ZoneOffset.UTC);
        failureReason = null;
    }

    public void markFailed(String reason) {
        ensurePending();
        status = NotificationStatus.FAILED;
        failureReason = requireText(reason, "failureReason");
    }

    public void markSkipped() {
        ensurePending();
        status = NotificationStatus.SKIPPED;
    }

    private void ensurePending() {
        if (status != NotificationStatus.PENDING) {
            throw new InvalidEntityStateException("Only pending notifications can be updated");
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field + " is required").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
