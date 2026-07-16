package com.syntagi.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access is denied"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "An account already exists for this email"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email or password is incorrect"),
    ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN, "User account is inactive"),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "User account is locked"),
    BUSINESS_ACCESS_NOT_FOUND(HttpStatus.FORBIDDEN, "No active business access was found"),
    BUSINESS_MEMBERSHIP_NOT_FOUND(HttpStatus.FORBIDDEN, "Business membership was not found"),
    FORBIDDEN_ROLE(HttpStatus.FORBIDDEN, "The current role cannot perform this operation"),
    DUPLICATE_STAFF_MEMBERSHIP(HttpStatus.CONFLICT, "User is already connected to this business"),
    INACTIVE_MEMBERSHIP(HttpStatus.FORBIDDEN, "Business membership is inactive"),
    STAFF_NOT_FOUND(HttpStatus.NOT_FOUND, "Staff membership was not found"),
    OWNER_SELF_DEACTIVATION_NOT_ALLOWED(
            HttpStatus.CONFLICT, "An owner cannot deactivate their own membership"),
    INVALID_TIMEZONE(HttpStatus.BAD_REQUEST, "Timezone is invalid"),
    SERVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "Service was not found"),
    DUPLICATE_SERVICE_CODE(HttpStatus.CONFLICT, "Service code already exists for this business"),
    DUPLICATE_SERVICE_NAME(HttpStatus.CONFLICT, "Service name already exists for this business"),
    APPOINTMENT_DURATION_REQUIRED(
            HttpStatus.BAD_REQUEST, "Appointment slot duration is required for this service mode"),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "Service schedule was not found"),
    SCHEDULE_OVERLAP(HttpStatus.CONFLICT, "Service schedule overlaps an active schedule"),
    INCOMPATIBLE_SERVICE_MODE(
            HttpStatus.BAD_REQUEST, "Schedule configuration is incompatible with the service mode"),
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "Operating end time must be after start time"),
    CROSS_BUSINESS_ACCESS_FORBIDDEN(
            HttpStatus.FORBIDDEN, "Resource belongs to another business"),
    WALK_IN_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "Walk-in queueing is not enabled for this service"),
    QUEUE_SESSION_NOT_FOUND(HttpStatus.CONFLICT, "No open queue session is available"),
    QUEUE_SERVICE_REQUIRED(
            HttpStatus.BAD_REQUEST, "serviceId is required when multiple queues are active"),
    QUEUE_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "Queue token was not found"),
    NO_CURRENT_QUEUE_TOKEN(HttpStatus.CONFLICT, "There is no current queue token"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource was not found"),
    CONFLICT(HttpStatus.CONFLICT, "Request conflicts with existing state"),
    INVALID_ENTITY_STATE(HttpStatus.CONFLICT, "Entity state does not allow this operation"),
    INVALID_QUEUE_TOKEN_TRANSITION(HttpStatus.CONFLICT, "Queue token status transition is invalid"),
    SLOT_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "Appointment slot has no available capacity"),
    QUEUE_SESSION_CLOSED(HttpStatus.CONFLICT, "Queue session is closed"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
