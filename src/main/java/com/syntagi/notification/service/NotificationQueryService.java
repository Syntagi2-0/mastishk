package com.syntagi.notification.service;

import com.syntagi.auth.service.AuthenticatedBusinessContext;
import com.syntagi.auth.service.AuthenticatedBusinessContextService;
import com.syntagi.common.exception.ApplicationException;
import com.syntagi.common.exception.ErrorCode;
import com.syntagi.notification.dto.response.*;
import com.syntagi.notification.entity.Notification;
import com.syntagi.notification.enums.*;
import com.syntagi.notification.repository.NotificationRepository;
import com.syntagi.notification.util.WhatsAppDeepLinkFormatter;
import com.syntagi.queue.entity.QueueToken;
import com.syntagi.queue.repository.QueueTokenRepository;
import java.time.*;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationQueryService {

    private final NotificationRepository repository;
    private final AuthenticatedBusinessContextService contextService;
    private final NotificationService notificationService;
    private final WhatsAppDeepLinkFormatter whatsAppFormatter;
    private final QueueTokenRepository queueTokenRepository;

    public NotificationQueryService(
            NotificationRepository repository,
            AuthenticatedBusinessContextService contextService,
            NotificationService notificationService,
            WhatsAppDeepLinkFormatter whatsAppFormatter,
            QueueTokenRepository queueTokenRepository) {
        this.repository = repository;
        this.contextService = contextService;
        this.notificationService = notificationService;
        this.whatsAppFormatter = whatsAppFormatter;
        this.queueTokenRepository = queueTokenRepository;
    }

    @Transactional(readOnly = true)
    public Page<PublicNotificationResponse> publicNotifications(
            String bookingReference, String tokenDisplay, String mobile, Pageable pageable) {
        boolean byBooking = hasText(bookingReference);
        boolean byToken = hasText(tokenDisplay);
        if (byBooking == byToken || !hasText(mobile)) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED,
                    "Provide mobile with exactly one of bookingReference or tokenDisplay");
        }
        String normalizedMobile = mobile.trim();
        UUID queueTokenId = byToken
                ? resolveQueueToken(tokenDisplay, normalizedMobile).getId()
                : null;
        Specification<Notification> specification = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("customer").get("mobile"), normalizedMobile));
            if (byBooking) {
                predicates.add(cb.equal(root.get("appointment").get("bookingReference"),
                        bookingReference.trim()));
            } else {
                predicates.add(cb.equal(root.get("queueToken").get("id"), queueTokenId));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return repository.findAll(specification, newest(pageable)).map(this::publicResponse);
    }

    private QueueToken resolveQueueToken(String tokenDisplay, String mobile) {
        return queueTokenRepository
                .findFirstByTokenDisplayAndCustomerMobileOrderByJoinedAtDesc(
                        tokenDisplay.trim().toUpperCase(Locale.ROOT), mobile)
                .orElseThrow(() -> new ApplicationException(ErrorCode.QUEUE_TOKEN_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Page<BusinessNotificationResponse> businessNotifications(
            NotificationStatus status,
            NotificationType type,
            NotificationChannel channel,
            LocalDate dateFrom,
            LocalDate dateTo,
            String customerMobile,
            String bookingReference,
            String tokenDisplay,
            String search,
            Pageable pageable) {
        AuthenticatedBusinessContext context = contextService.current();
        if (dateFrom != null && dateTo != null && dateTo.isBefore(dateFrom)) {
            throw new ApplicationException(ErrorCode.INVALID_TIME_RANGE,
                    "dateTo must be on or after dateFrom");
        }
        ZoneId zone = ZoneId.of(context.business().getTimezone());
        OffsetDateTime from = dateFrom == null ? null
                : dateFrom.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime to = dateTo == null ? null
                : dateTo.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        Specification<Notification> specification = filters(
                context.business().getId(), status, type, channel, from, to,
                customerMobile, bookingReference, tokenDisplay, search);
        return repository.findAll(specification, newest(pageable)).map(this::businessResponse);
    }

    @Transactional(readOnly = true)
    public PendingNotificationCountResponse pendingCount() {
        UUID businessId = contextService.current().business().getId();
        return new PendingNotificationCountResponse(
                repository.countByBusinessIdAndStatus(businessId, NotificationStatus.PENDING));
    }

    @Transactional
    public BusinessNotificationResponse markSent(UUID notificationId) {
        UUID businessId = contextService.current().business().getId();
        Notification existing = repository.findById(notificationId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Notification was not found"));
        if (!existing.getBusiness().getId().equals(businessId)) {
            throw new ApplicationException(ErrorCode.CROSS_BUSINESS_ACCESS_FORBIDDEN);
        }
        return businessResponse(notificationService.markSent(notificationId));
    }

    private PublicNotificationResponse publicResponse(Notification notification) {
        return new PublicNotificationResponse(
                notification.getNotificationType(), notification.getTitle(), notification.getMessage(),
                notification.getChannel(), notification.getStatus(), notification.getCreatedAt(),
                notification.getSentAt(), deepLink(notification));
    }

    private BusinessNotificationResponse businessResponse(Notification notification) {
        return new BusinessNotificationResponse(
                notification.getId(), notification.getNotificationType(), notification.getTitle(),
                notification.getMessage(), notification.getChannel(), notification.getStatus(),
                notification.getCreatedAt(), notification.getSentAt(),
                notification.getCustomer() == null ? null : notification.getCustomer().getMobile(),
                notification.getAppointment() == null
                        ? null : notification.getAppointment().getBookingReference(),
                notification.getQueueToken() == null
                        ? null : notification.getQueueToken().getTokenDisplay(),
                deepLink(notification));
    }

    private String deepLink(Notification notification) {
        return notification.getChannel() == NotificationChannel.WHATSAPP_LINK
                && notification.getCustomer() != null
                ? whatsAppFormatter.create(
                        notification.getCustomer().getMobile(), notification.getMessage())
                : null;
    }

    private static Specification<Notification> filters(
            UUID businessId,
            NotificationStatus status,
            NotificationType type,
            NotificationChannel channel,
            OffsetDateTime from,
            OffsetDateTime to,
            String customerMobile,
            String bookingReference,
            String tokenDisplay,
            String search) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("business").get("id"), businessId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (type != null) predicates.add(cb.equal(root.get("notificationType"), type));
            if (channel != null) predicates.add(cb.equal(root.get("channel"), channel));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) predicates.add(cb.lessThan(root.get("createdAt"), to));
            if (hasText(customerMobile)) {
                predicates.add(cb.equal(root.get("customer").get("mobile"), customerMobile.trim()));
            }
            if (hasText(bookingReference)) {
                predicates.add(cb.equal(root.get("appointment").get("bookingReference"),
                        bookingReference.trim()));
            }
            if (hasText(tokenDisplay)) {
                predicates.add(cb.equal(root.get("queueToken").get("tokenDisplay"),
                        tokenDisplay.trim().toUpperCase(Locale.ROOT)));
            }
            if (hasText(search)) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("message")), pattern),
                        cb.like(cb.lower(root.get("customer").get("mobile")), pattern),
                        cb.like(cb.lower(root.get("appointment").get("bookingReference")), pattern),
                        cb.like(cb.lower(root.get("queueToken").get("tokenDisplay")), pattern)));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private static Pageable newest(Pageable pageable) {
        return org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(),
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.desc("createdAt")));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
