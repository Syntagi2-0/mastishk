package com.syntagi.notification.controller;

import com.syntagi.common.api.ApiResponse;
import com.syntagi.notification.dto.response.*;
import com.syntagi.notification.enums.*;
import com.syntagi.notification.service.NotificationQueryService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationQueryService queryService;

    public NotificationController(NotificationQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ApiResponse<Page<BusinessNotificationResponse>> list(
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) NotificationType notificationType,
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String customerMobile,
            @RequestParam(required = false) String bookingReference,
            @RequestParam(required = false) String tokenDisplay,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ApiResponse.success(queryService.businessNotifications(
                status, notificationType, channel, dateFrom, dateTo, customerMobile,
                bookingReference, tokenDisplay, search, pageable));
    }

    @GetMapping("/pending-count")
    public ApiResponse<PendingNotificationCountResponse> pendingCount() {
        return ApiResponse.success(queryService.pendingCount());
    }

    @PostMapping("/{notificationId}/mark-sent")
    public ApiResponse<BusinessNotificationResponse> markSent(@PathVariable UUID notificationId) {
        return ApiResponse.success(queryService.markSent(notificationId), "Notification marked sent");
    }
}
