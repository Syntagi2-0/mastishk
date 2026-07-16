package com.syntagi.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(
        boolean success,
        String code,
        String message,
        String path,
        Map<String, String> validationErrors,
        Instant timestamp) {

    public static ApiErrorResponse of(String code, String message, String path) {
        return new ApiErrorResponse(false, code, message, path, Map.of(), Instant.now());
    }

    public static ApiErrorResponse validation(
            String code, String message, String path, Map<String, String> errors) {
        return new ApiErrorResponse(false, code, message, path, errors, Instant.now());
    }
}
