package com.syntagi.notification.enums;

public enum NotificationType {
    APPOINTMENT_BOOKED,
    APPOINTMENT_CANCELLED,
    APPOINTMENT_TOKEN_GENERATED,
    QUEUE_TOKEN_CALLED,
    QUEUE_TOKEN_SKIPPED,
    QUEUE_TOKEN_CANCELLED,
    // Retained for compatibility with any records created before the MVP notification module.
    APPOINTMENT_CONFIRMED,
    QUEUE_JOINED,
    TOKEN_CALLED,
    QUEUE_UPDATE
}
