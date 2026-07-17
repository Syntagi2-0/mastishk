package com.syntagi.appointment.entity;

import com.syntagi.appointment.enums.AppointmentStatus;
import com.syntagi.business.entity.Business;
import com.syntagi.common.exception.InvalidEntityStateException;
import com.syntagi.common.persistence.BaseEntity;
import com.syntagi.customer.entity.Customer;
import com.syntagi.servicecatalog.entity.BusinessService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "appointments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Appointment extends BaseEntity {

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
    @JoinColumn(name = "appointment_slot_id")
    private AppointmentSlot appointmentSlot;

    @Column(name = "booking_reference", nullable = false, length = 40)
    private String bookingReference;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "scheduled_start_time", nullable = false)
    private LocalTime scheduledStartTime;

    @Column(name = "scheduled_end_time")
    private LocalTime scheduledEndTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AppointmentStatus status = AppointmentStatus.CONFIRMED;

    @Column(name = "customer_notes", length = 500)
    private String customerNotes;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    public Appointment(
            Business business,
            BusinessService businessService,
            Customer customer,
            AppointmentSlot appointmentSlot,
            String bookingReference,
            LocalDate appointmentDate,
            LocalTime scheduledStartTime,
            LocalTime scheduledEndTime,
            String customerNotes) {
        this.business = Objects.requireNonNull(business, "business is required");
        this.businessService = Objects.requireNonNull(
                businessService, "businessService is required");
        this.customer = Objects.requireNonNull(customer, "customer is required");
        this.appointmentSlot = appointmentSlot;
        this.bookingReference = requireText(bookingReference, "bookingReference");
        this.appointmentDate = Objects.requireNonNull(
                appointmentDate, "appointmentDate is required");
        this.scheduledStartTime = Objects.requireNonNull(
                scheduledStartTime, "scheduledStartTime is required");
        this.scheduledEndTime = scheduledEndTime;
        this.customerNotes = trimToNull(customerNotes);
        validate();
    }

    public void cancel(String reason, OffsetDateTime cancelledAt) {
        requireStatus(AppointmentStatus.CONFIRMED);
        this.status = AppointmentStatus.CANCELLED;
        this.cancellationReason = trimToNull(reason);
        this.cancelledAt = Objects.requireNonNull(cancelledAt, "cancelledAt is required");
    }

    public void complete() {
        requireStatus(AppointmentStatus.CONFIRMED);
        status = AppointmentStatus.COMPLETED;
    }

    public void markNoShow() {
        requireStatus(AppointmentStatus.CONFIRMED);
        status = AppointmentStatus.NO_SHOW;
    }

    public boolean isConfirmed() {
        return status == AppointmentStatus.CONFIRMED;
    }

    @PrePersist
    @PreUpdate
    void validate() {
        bookingReference = requireText(bookingReference, "bookingReference");
        customerNotes = trimToNull(customerNotes);
        cancellationReason = trimToNull(cancellationReason);
        if (scheduledEndTime != null && !scheduledEndTime.isAfter(scheduledStartTime)) {
            throw new InvalidEntityStateException(
                    "Scheduled end time must be after scheduled start time");
        }
    }

    private void requireStatus(AppointmentStatus required) {
        if (status != required) {
            throw new InvalidEntityStateException(
                    "Appointment must be " + required + " for this operation");
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
