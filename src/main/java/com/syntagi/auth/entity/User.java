package com.syntagi.auth.entity;

import com.syntagi.auth.enums.UserStatus;
import com.syntagi.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 20)
    private String mobile;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    public User(String fullName, String email, String mobile, String passwordHash) {
        this.fullName = requireText(fullName, "fullName");
        this.email = normalizeEmail(email);
        this.mobile = trimToNull(mobile);
        this.passwordHash = requireText(passwordHash, "passwordHash");
        this.status = UserStatus.ACTIVE;
    }

    public void activate() {
        status = UserStatus.ACTIVE;
    }

    public void deactivate() {
        status = UserStatus.INACTIVE;
    }

    public void lock() {
        status = UserStatus.LOCKED;
    }

    public void updateLastLogin() {
        lastLoginAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @PrePersist
    @PreUpdate
    void normalize() {
        fullName = requireText(fullName, "fullName");
        email = normalizeEmail(email);
        mobile = trimToNull(mobile);
    }

    private static String normalizeEmail(String value) {
        return requireText(value, "email").toLowerCase(Locale.ROOT);
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
