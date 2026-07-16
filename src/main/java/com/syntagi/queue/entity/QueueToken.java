package com.syntagi.queue.entity;

import com.syntagi.appointment.entity.Appointment;
import com.syntagi.business.entity.Business;
import com.syntagi.common.persistence.BaseEntity;
import com.syntagi.customer.entity.Customer;
import com.syntagi.queue.enums.QueueTokenSourceType;
import com.syntagi.queue.enums.QueueTokenStatus;
import com.syntagi.queue.exception.InvalidQueueTokenTransitionException;
import com.syntagi.servicecatalog.entity.BusinessService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "queue_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QueueToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "queue_session_id", nullable = false)
    private QueueSession queueSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_service_id", nullable = false)
    private BusinessService businessService;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @Column(name = "token_number", nullable = false)
    private int tokenNumber;

    @Column(name = "token_display", nullable = false, updatable = false, length = 30)
    private String tokenDisplay;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private QueueTokenSourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QueueTokenStatus status = QueueTokenStatus.WAITING;

    @Column(name = "scheduled_time")
    private LocalTime scheduledTime;

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    @Column(name = "called_at")
    private OffsetDateTime calledAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "skipped_at")
    private OffsetDateTime skippedAt;

    @Column(name = "queue_order", nullable = false)
    private long queueOrder;

    @Column(nullable = false)
    private int priority;

    @Column(length = 500)
    private String notes;

    public QueueToken(
            QueueSession queueSession,
            Business business,
            BusinessService businessService,
            Customer customer,
            Appointment appointment,
            int tokenNumber,
            String tokenDisplay,
            QueueTokenSourceType sourceType,
            LocalTime scheduledTime,
            OffsetDateTime joinedAt,
            long queueOrder,
            int priority,
            String notes) {
        this.queueSession = Objects.requireNonNull(queueSession, "queueSession is required");
        this.business = Objects.requireNonNull(business, "business is required");
        this.businessService = Objects.requireNonNull(
                businessService, "businessService is required");
        this.customer = Objects.requireNonNull(customer, "customer is required");
        this.appointment = appointment;
        if (tokenNumber <= 0) {
            throw new IllegalArgumentException("tokenNumber must be greater than zero");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("priority cannot be negative");
        }
        this.tokenNumber = tokenNumber;
        this.tokenDisplay = requireText(tokenDisplay, "tokenDisplay");
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType is required");
        this.scheduledTime = scheduledTime;
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt is required");
        this.queueOrder = queueOrder;
        this.priority = priority;
        this.notes = trimToNull(notes);
    }

    public void call(OffsetDateTime calledAt) {
        requireTransition(QueueTokenStatus.CALLED,
                QueueTokenStatus.WAITING, QueueTokenStatus.SKIPPED);
        status = QueueTokenStatus.CALLED;
        this.calledAt = Objects.requireNonNull(calledAt, "calledAt is required");
    }

    public void complete(OffsetDateTime completedAt) {
        requireTransition(QueueTokenStatus.COMPLETED, QueueTokenStatus.CALLED);
        status = QueueTokenStatus.COMPLETED;
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt is required");
    }

    public void skip(OffsetDateTime skippedAt) {
        requireTransition(QueueTokenStatus.SKIPPED, QueueTokenStatus.CALLED);
        status = QueueTokenStatus.SKIPPED;
        this.skippedAt = Objects.requireNonNull(skippedAt, "skippedAt is required");
    }

    public void cancel(OffsetDateTime cancelledAt) {
        requireTransition(QueueTokenStatus.CANCELLED,
                QueueTokenStatus.WAITING, QueueTokenStatus.CALLED, QueueTokenStatus.SKIPPED);
        status = QueueTokenStatus.CANCELLED;
        this.cancelledAt = Objects.requireNonNull(cancelledAt, "cancelledAt is required");
    }

    public void markNoShow() {
        requireTransition(QueueTokenStatus.NO_SHOW,
                QueueTokenStatus.WAITING, QueueTokenStatus.SKIPPED);
        status = QueueTokenStatus.NO_SHOW;
    }

    public void returnToWaiting() {
        requireTransition(QueueTokenStatus.WAITING, QueueTokenStatus.SKIPPED);
        status = QueueTokenStatus.WAITING;
    }

    public boolean isWaiting() {
        return status == QueueTokenStatus.WAITING;
    }

    public boolean isCurrent() {
        QueueToken current = queueSession.getCurrentToken();
        return current == this || (current != null && current.equals(this));
    }

    private void requireTransition(QueueTokenStatus target, QueueTokenStatus... allowedSources) {
        for (QueueTokenStatus allowedSource : allowedSources) {
            if (status == allowedSource) {
                return;
            }
        }
        throw new InvalidQueueTokenTransitionException(status, target);
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
