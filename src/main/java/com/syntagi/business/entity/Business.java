package com.syntagi.business.entity;

import com.syntagi.business.enums.BusinessStatus;
import com.syntagi.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "businesses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Business extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 120)
    private String slug;

    @Column(name = "business_type", nullable = false, length = 50)
    private String businessType;

    @Column(length = 255)
    private String email;

    @Column(length = 20)
    private String mobile;

    @Column(name = "address_line", length = 500)
    private String addressLine;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country_code", nullable = false, length = 5)
    private String countryCode = "IN";

    @Column(nullable = false, length = 80)
    private String timezone = "Asia/Kolkata";

    @Column(name = "public_queue_code", nullable = false, length = 50)
    private String publicQueueCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BusinessStatus status = BusinessStatus.ACTIVE;

    public Business(String name, String slug, String businessType, String publicQueueCode) {
        this.name = requireText(name, "name");
        this.slug = normalizeSlug(slug);
        this.businessType = requireText(businessType, "businessType");
        this.publicQueueCode = requireText(publicQueueCode, "publicQueueCode");
    }

    public void updateProfile(String name, String businessType) {
        this.name = requireText(name, "name");
        this.businessType = requireText(businessType, "businessType");
    }

    public void updateContactDetails(String email, String mobile) {
        this.email = normalizeNullableEmail(email);
        this.mobile = trimToNull(mobile);
    }

    public void updateAddress(
            String addressLine,
            String city,
            String state,
            String postalCode,
            String countryCode,
            String timezone) {
        this.addressLine = trimToNull(addressLine);
        this.city = trimToNull(city);
        this.state = trimToNull(state);
        this.postalCode = trimToNull(postalCode);
        this.countryCode = normalizeCountryCode(countryCode);
        this.timezone = requireText(timezone, "timezone");
    }

    public void activate() {
        status = BusinessStatus.ACTIVE;
    }

    public void deactivate() {
        status = BusinessStatus.INACTIVE;
    }

    public void suspend() {
        status = BusinessStatus.SUSPENDED;
    }

    @PrePersist
    @PreUpdate
    void normalize() {
        name = requireText(name, "name");
        slug = normalizeSlug(slug);
        businessType = requireText(businessType, "businessType");
        email = normalizeNullableEmail(email);
        mobile = trimToNull(mobile);
        addressLine = trimToNull(addressLine);
        city = trimToNull(city);
        state = trimToNull(state);
        postalCode = trimToNull(postalCode);
        countryCode = normalizeCountryCode(countryCode);
        timezone = requireText(timezone, "timezone");
        publicQueueCode = requireText(publicQueueCode, "publicQueueCode");
    }

    private static String normalizeSlug(String value) {
        return requireText(value, "slug").toLowerCase(Locale.ROOT);
    }

    private static String normalizeNullableEmail(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private static String normalizeCountryCode(String value) {
        return requireText(value, "countryCode").toUpperCase(Locale.ROOT);
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
