package com.syntagi.servicecatalog.entity;

import com.syntagi.common.exception.InvalidEntityStateException;
import com.syntagi.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "service_schedules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServiceSchedule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_service_id", nullable = false)
    private BusinessService businessService;

    @Convert(converter = DayOfWeekConverter.class)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(name = "operating_start_time", nullable = false)
    private LocalTime operatingStartTime;

    @Column(name = "operating_end_time", nullable = false)
    private LocalTime operatingEndTime;

    @Column(name = "queue_open_before_minutes", nullable = false)
    private int queueOpenBeforeMinutes;

    @Column(name = "appointment_booking_enabled", nullable = false)
    private boolean appointmentBookingEnabled = true;

    @Column(name = "walk_in_enabled", nullable = false)
    private boolean walkInEnabled = true;

    @Column(nullable = false)
    private boolean active = true;

    public ServiceSchedule(
            BusinessService businessService,
            DayOfWeek dayOfWeek,
            LocalTime operatingStartTime,
            LocalTime operatingEndTime) {
        this.businessService = Objects.requireNonNull(businessService, "businessService is required");
        this.dayOfWeek = Objects.requireNonNull(dayOfWeek, "dayOfWeek is required");
        this.operatingStartTime = Objects.requireNonNull(
                operatingStartTime, "operatingStartTime is required");
        this.operatingEndTime = Objects.requireNonNull(
                operatingEndTime, "operatingEndTime is required");
        validate();
    }

    public void configureQueueOpening(int queueOpenBeforeMinutes) {
        this.queueOpenBeforeMinutes = queueOpenBeforeMinutes;
        validate();
    }

    public void configureBookingModes(
            boolean appointmentBookingEnabled, boolean walkInEnabled) {
        this.appointmentBookingEnabled = appointmentBookingEnabled;
        this.walkInEnabled = walkInEnabled;
    }

    public void updateConfiguration(
            DayOfWeek dayOfWeek,
            LocalTime operatingStartTime,
            LocalTime operatingEndTime,
            int queueOpenBeforeMinutes,
            boolean appointmentBookingEnabled,
            boolean walkInEnabled) {
        this.dayOfWeek = Objects.requireNonNull(dayOfWeek, "dayOfWeek is required");
        this.operatingStartTime = Objects.requireNonNull(
                operatingStartTime, "operatingStartTime is required");
        this.operatingEndTime = Objects.requireNonNull(
                operatingEndTime, "operatingEndTime is required");
        this.queueOpenBeforeMinutes = queueOpenBeforeMinutes;
        this.appointmentBookingEnabled = appointmentBookingEnabled;
        this.walkInEnabled = walkInEnabled;
        validate();
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    public boolean isOpenAt(LocalTime time) {
        Objects.requireNonNull(time, "time is required");
        return !time.isBefore(operatingStartTime) && time.isBefore(operatingEndTime);
    }

    public LocalTime calculateQueueOpeningTime() {
        return operatingStartTime.minusMinutes(queueOpenBeforeMinutes);
    }

    @PrePersist
    @PreUpdate
    void validate() {
        if (operatingStartTime == null || operatingEndTime == null
                || !operatingEndTime.isAfter(operatingStartTime)) {
            throw new InvalidEntityStateException(
                    "Operating end time must be after operating start time");
        }
        if (queueOpenBeforeMinutes < 0 || queueOpenBeforeMinutes > 1440) {
            throw new InvalidEntityStateException(
                    "Queue opening lead time must be between 0 and 1440 minutes");
        }
    }
}
