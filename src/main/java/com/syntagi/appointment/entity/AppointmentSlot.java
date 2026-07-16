package com.syntagi.appointment.entity;

import com.syntagi.appointment.enums.AppointmentSlotStatus;
import com.syntagi.appointment.exception.SlotCapacityExceededException;
import com.syntagi.business.entity.Business;
import com.syntagi.common.exception.InvalidEntityStateException;
import com.syntagi.common.persistence.BaseEntity;
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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "appointment_slots")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppointmentSlot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_service_id", nullable = false)
    private BusinessService businessService;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "booked_count", nullable = false)
    private int bookedCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AppointmentSlotStatus status = AppointmentSlotStatus.AVAILABLE;

    public AppointmentSlot(
            Business business,
            BusinessService businessService,
            LocalDate slotDate,
            LocalTime startTime,
            LocalTime endTime,
            int capacity) {
        this.business = Objects.requireNonNull(business, "business is required");
        this.businessService = Objects.requireNonNull(
                businessService, "businessService is required");
        this.slotDate = Objects.requireNonNull(slotDate, "slotDate is required");
        this.startTime = Objects.requireNonNull(startTime, "startTime is required");
        this.endTime = Objects.requireNonNull(endTime, "endTime is required");
        this.capacity = capacity;
        validate();
    }

    public boolean hasAvailability() {
        return status == AppointmentSlotStatus.AVAILABLE && bookedCount < capacity;
    }

    public void reserveOne() {
        if (!hasAvailability()) {
            throw new SlotCapacityExceededException();
        }
        bookedCount++;
    }

    public void releaseOne() {
        if (bookedCount == 0) {
            throw new InvalidEntityStateException("Booked count cannot become negative");
        }
        bookedCount--;
    }

    public void block() {
        status = AppointmentSlotStatus.BLOCKED;
    }

    public void close() {
        status = AppointmentSlotStatus.CLOSED;
    }

    public void makeAvailable() {
        status = AppointmentSlotStatus.AVAILABLE;
    }

    @PrePersist
    @PreUpdate
    void validate() {
        if (capacity <= 0) {
            throw new InvalidEntityStateException("Slot capacity must be greater than zero");
        }
        if (bookedCount < 0 || bookedCount > capacity) {
            throw new InvalidEntityStateException("Booked count must be between zero and capacity");
        }
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new InvalidEntityStateException("Slot end time must be after start time");
        }
    }
}
