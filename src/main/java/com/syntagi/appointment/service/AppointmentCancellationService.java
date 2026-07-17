package com.syntagi.appointment.service;

import com.syntagi.appointment.entity.Appointment;
import com.syntagi.appointment.entity.AppointmentSlot;
import com.syntagi.appointment.enums.AppointmentStatus;
import com.syntagi.appointment.repository.AppointmentRepository;
import com.syntagi.appointment.repository.AppointmentSlotRepository;
import com.syntagi.common.exception.ApplicationException;
import com.syntagi.common.exception.ErrorCode;
import com.syntagi.queue.entity.QueueToken;
import com.syntagi.queue.entity.QueueSession;
import com.syntagi.queue.enums.QueueTokenStatus;
import com.syntagi.queue.repository.QueueTokenRepository;
import com.syntagi.queue.repository.QueueSessionRepository;
import com.syntagi.queue.service.QueueTimeService;
import com.syntagi.queue.service.QueueTokenLifecycleService;
import com.syntagi.notification.service.NotificationService;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AppointmentCancellationService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotRepository slotRepository;
    private final QueueTokenRepository tokenRepository;
    private final QueueSessionRepository sessionRepository;
    private final QueueTimeService timeService;
    private final QueueTokenLifecycleService tokenLifecycleService;
    private final NotificationService notificationService;

    public AppointmentCancellationService(
            AppointmentRepository appointmentRepository,
            AppointmentSlotRepository slotRepository,
            QueueTokenRepository tokenRepository,
            QueueSessionRepository sessionRepository,
            QueueTimeService timeService,
            QueueTokenLifecycleService tokenLifecycleService,
            NotificationService notificationService) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.tokenRepository = tokenRepository;
        this.sessionRepository = sessionRepository;
        this.timeService = timeService;
        this.tokenLifecycleService = tokenLifecycleService;
        this.notificationService = notificationService;
    }

    public Appointment cancelByReference(String bookingReference, String mobile, String reason) {
        UUID appointmentId = appointmentRepository
                .findIdByBookingReference(bookingReference.trim())
                .orElseThrow(() -> new ApplicationException(ErrorCode.APPOINTMENT_NOT_FOUND));
        QueueSession lockedSession = lockQueueSessionIfTokenExists(appointmentId);
        Appointment appointment = appointmentRepository
                .findByBookingReferenceForUpdate(bookingReference.trim())
                .orElseThrow(() -> new ApplicationException(ErrorCode.APPOINTMENT_NOT_FOUND));
        if (!appointment.getCustomer().getMobile().equals(mobile.trim())) {
            throw new ApplicationException(ErrorCode.MOBILE_MISMATCH);
        }
        return cancel(appointment, reason, true, lockedSession);
    }

    public Appointment cancelByBusiness(UUID appointmentId, UUID businessId, String reason) {
        QueueSession lockedSession = tokenRepository
                .findQueueSessionIdByAppointmentIdAndBusinessId(appointmentId, businessId)
                .map(this::lockQueueSession)
                .orElse(null);
        Appointment appointment = appointmentRepository
                .findByIdAndBusinessIdForUpdate(appointmentId, businessId)
                .orElseThrow(() -> appointmentRepository.existsById(appointmentId)
                        ? new ApplicationException(ErrorCode.CROSS_BUSINESS_ACCESS_FORBIDDEN)
                        : new ApplicationException(ErrorCode.APPOINTMENT_NOT_FOUND));
        return cancel(appointment, reason, false, lockedSession);
    }

    private Appointment cancel(
            Appointment appointment,
            String reason,
            boolean publicApi,
            QueueSession lockedSession) {
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new ApplicationException(ErrorCode.APPOINTMENT_ALREADY_CANCELLED);
        }
        if (!appointment.isConfirmed()) {
            throw new ApplicationException(ErrorCode.INVALID_APPOINTMENT_STATUS_TRANSITION);
        }
        QueueToken token = tokenRepository.findByAppointmentId(appointment.getId()).orElse(null);
        if (publicApi && token != null
                && (token.getStatus() == QueueTokenStatus.CALLED
                    || token.getStatus() == QueueTokenStatus.COMPLETED)) {
            throw new ApplicationException(ErrorCode.INVALID_APPOINTMENT_STATUS_TRANSITION,
                    "A called or completed queue token cannot be cancelled publicly");
        }
        AppointmentSlot slot = appointment.getAppointmentSlot();
        if (slot != null) {
            slot = slotRepository.findByIdAndBusinessServiceIdForUpdate(
                            slot.getId(), appointment.getBusinessService().getId())
                    .orElseThrow(() -> new ApplicationException(ErrorCode.SLOT_NOT_FOUND));
            slot.releaseOne();
        }
        if (token != null && token.getStatus() == QueueTokenStatus.WAITING) {
            tokenLifecycleService.cancel(token, timeService.nowOffset());
        } else if (!publicApi && token != null
                && (token.getStatus() == QueueTokenStatus.CALLED
                    || token.getStatus() == QueueTokenStatus.SKIPPED)) {
            tokenLifecycleService.cancel(token, timeService.nowOffset());
        }
        if (token != null && lockedSession != null
                && lockedSession.getCurrentToken() != null
                && lockedSession.getCurrentToken().getId().equals(token.getId())) {
            lockedSession.clearCurrentToken();
        }
        appointment.cancel(reason, timeService.nowOffset());
        notificationService.appointmentCancelled(appointment);
        return appointment;
    }

    private QueueSession lockQueueSessionIfTokenExists(UUID appointmentId) {
        return tokenRepository.findQueueSessionIdByAppointmentId(appointmentId)
                .map(this::lockQueueSession)
                .orElse(null);
    }

    private QueueSession lockQueueSession(UUID sessionId) {
        return sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.QUEUE_SESSION_NOT_FOUND));
    }
}
