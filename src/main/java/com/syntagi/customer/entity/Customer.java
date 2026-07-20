package com.syntagi.customer.entity;

import com.syntagi.business.entity.Business;
import com.syntagi.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "customers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 20)
    private String mobile;

    @Column(length = 255)
    private String email;

    @Column(length = 30)
    private String gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 500)
    private String address;

    @Column(length = 500)
    private String notes;

    public Customer(Business business, String fullName, String mobile, String email) {
        this(business, fullName, mobile, email, null, null, null, null);
    }

    public Customer(
            Business business,
            String fullName,
            String mobile,
            String email,
            String gender,
            LocalDate dateOfBirth,
            String address,
            String notes) {
        this.business = Objects.requireNonNull(business, "business is required");
        this.fullName = requireText(fullName, "fullName");
        this.mobile = requireText(mobile, "mobile");
        this.email = normalizeEmail(email);
        this.gender = trimToNull(gender);
        this.dateOfBirth = dateOfBirth;
        this.address = trimToNull(address);
        this.notes = trimToNull(notes);
    }

    @PrePersist
    @PreUpdate
    void normalize() {
        fullName = requireText(fullName, "fullName");
        mobile = requireText(mobile, "mobile");
        email = normalizeEmail(email);
        gender = trimToNull(gender);
        address = trimToNull(address);
        notes = trimToNull(notes);
    }

    private static String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field + " is required").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }
}
