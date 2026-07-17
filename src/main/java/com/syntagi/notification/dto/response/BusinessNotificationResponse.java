package com.syntagi.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.syntagi.notification.enums.NotificationChannel;
import com.syntagi.notification.enums.NotificationStatus;
import com.syntagi.notification.enums.NotificationType;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BusinessNotificationResponse(
        UUID notificationId,
        NotificationType notificationType,
        String title,
        String message,
        NotificationChannel channel,
        NotificationStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime sentAt,
        String customerMobile,
        String bookingReference,
        String tokenDisplay,
        String whatsappDeepLink) {
}
