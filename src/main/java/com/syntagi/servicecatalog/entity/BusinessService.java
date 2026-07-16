package com.syntagi.servicecatalog.entity;

import com.syntagi.business.entity.Business;
import com.syntagi.common.exception.InvalidEntityStateException;
import com.syntagi.common.persistence.BaseEntity;
import com.syntagi.servicecatalog.enums.ServiceMode;
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
import java.util.Objects;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "business_services")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessService extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "service_code", nullable = false, length = 50)
    private String serviceCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_mode", nullable = false, length = 30)
    private ServiceMode serviceMode;

    @Column(name = "expected_duration_minutes")
    private Integer expectedDurationMinutes;

    @Column(name = "appointment_slot_duration_minutes")
    private Integer appointmentSlotDurationMinutes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public BusinessService(
            Business business, String name, String serviceCode, ServiceMode serviceMode) {
        this.business = Objects.requireNonNull(business, "business is required");
        this.name = requireText(name, "name");
        this.serviceCode = normalizeServiceCode(serviceCode);
        this.serviceMode = Objects.requireNonNull(serviceMode, "serviceMode is required");
    }

    public void updateDetails(
            String description,
            Integer expectedDurationMinutes,
            Integer appointmentSlotDurationMinutes,
            int displayOrder) {
        requirePositive(expectedDurationMinutes, "expectedDurationMinutes");
        requirePositive(appointmentSlotDurationMinutes, "appointmentSlotDurationMinutes");
        this.description = trimToNull(description);
        this.expectedDurationMinutes = expectedDurationMinutes;
        this.appointmentSlotDurationMinutes = appointmentSlotDurationMinutes;
        this.displayOrder = displayOrder;
    }

    public void updateDefinition(
            String name,
            String description,
            String serviceCode,
            ServiceMode serviceMode,
            Integer expectedDurationMinutes,
            Integer appointmentSlotDurationMinutes,
            int displayOrder) {
        this.name = requireText(name, "name");
        this.serviceCode = normalizeServiceCode(serviceCode);
        this.serviceMode = Objects.requireNonNull(serviceMode, "serviceMode is required");
        updateDetails(
                description,
                expectedDurationMinutes,
                appointmentSlotDurationMinutes,
                displayOrder);
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    public void updateMode(ServiceMode serviceMode) {
        this.serviceMode = Objects.requireNonNull(serviceMode, "serviceMode is required");
    }

    public boolean supportsWalkIn() {
        return serviceMode == ServiceMode.WALK_IN || serviceMode == ServiceMode.BOTH;
    }

    public boolean supportsAppointment() {
        return serviceMode == ServiceMode.APPOINTMENT || serviceMode == ServiceMode.BOTH;
    }

    @PrePersist
    @PreUpdate
    void validateAndNormalize() {
        name = requireText(name, "name");
        serviceCode = normalizeServiceCode(serviceCode);
        description = trimToNull(description);
        requirePositive(expectedDurationMinutes, "expectedDurationMinutes");
        requirePositive(appointmentSlotDurationMinutes, "appointmentSlotDurationMinutes");
    }

    private static void requirePositive(Integer value, String field) {
        if (value != null && value <= 0) {
            throw new InvalidEntityStateException(field + " must be greater than zero");
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field + " is required").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private static String normalizeServiceCode(String value) {
        return requireText(value, "serviceCode").toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
