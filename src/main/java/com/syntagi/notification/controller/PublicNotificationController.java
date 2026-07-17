package com.syntagi.notification.controller;

import com.syntagi.common.api.ApiResponse;
import com.syntagi.notification.dto.response.PublicNotificationResponse;
import com.syntagi.notification.service.NotificationQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

@RestController
@SecurityRequirements
@RequestMapping("/api/public/notifications")
public class PublicNotificationController {

    private final NotificationQueryService queryService;

    public PublicNotificationController(NotificationQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ApiResponse<Page<PublicNotificationResponse>> list(
            @RequestParam(required = false) String bookingReference,
            @RequestParam(required = false) String tokenDisplay,
            @RequestParam String mobile,
            Pageable pageable) {
        return ApiResponse.success(queryService.publicNotifications(
                bookingReference, tokenDisplay, mobile, pageable));
    }
}
