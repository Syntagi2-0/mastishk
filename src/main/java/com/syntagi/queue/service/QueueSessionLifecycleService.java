package com.syntagi.queue.service;

import com.syntagi.auth.service.AuthenticatedBusinessContext;
import com.syntagi.auth.service.AuthenticatedBusinessContextService;
import com.syntagi.common.exception.ApplicationException;
import com.syntagi.common.exception.ErrorCode;
import com.syntagi.queue.dto.request.CreateQueueSessionForQueueRequest;
import com.syntagi.queue.dto.request.CreateQueueSessionRequest;
import com.syntagi.queue.dto.response.QueueSessionResponse;
import com.syntagi.queue.entity.QueueConfiguration;
import com.syntagi.queue.entity.QueueSession;
import com.syntagi.queue.repository.QueueConfigurationRepository;
import com.syntagi.queue.repository.QueueSessionRepository;
import com.syntagi.servicecatalog.entity.BusinessService;
import com.syntagi.servicecatalog.entity.ServiceSchedule;
import com.syntagi.servicecatalog.repository.BusinessServiceRepository;
import com.syntagi.servicecatalog.repository.ServiceScheduleRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueueSessionLifecycleService {

    private final AuthenticatedBusinessContextService contextService;
    private final QueueConfigurationRepository queueRepository;
    private final BusinessServiceRepository serviceRepository;
    private final ServiceScheduleRepository scheduleRepository;
    private final QueueSessionRepository sessionRepository;
    private final QueueTimeService timeService;
    private final AppointmentQueueTokenCoordinator appointmentTokenCoordinator;

    public QueueSessionLifecycleService(
            AuthenticatedBusinessContextService contextService,
            QueueConfigurationRepository queueRepository,
            BusinessServiceRepository serviceRepository,
            ServiceScheduleRepository scheduleRepository,
            QueueSessionRepository sessionRepository,
            QueueTimeService timeService,
            AppointmentQueueTokenCoordinator appointmentTokenCoordinator) {
        this.contextService = contextService;
        this.queueRepository = queueRepository;
        this.serviceRepository = serviceRepository;
        this.scheduleRepository = scheduleRepository;
        this.sessionRepository = sessionRepository;
        this.timeService = timeService;
        this.appointmentTokenCoordinator = appointmentTokenCoordinator;
    }

    /** Compatibility endpoint retained for existing service-based clients. */
    @Transactional
    public QueueSessionResponse createToday(CreateQueueSessionRequest request) {
        AuthenticatedBusinessContext context = contextService.current();
        BusinessService service = serviceRepository
                .findByIdAndBusinessIdForUpdate(
                        request.serviceId(), context.business().getId())
                .orElseThrow(() -> new ApplicationException(
                        serviceRepository.existsById(request.serviceId())
                                ? ErrorCode.CROSS_BUSINESS_ACCESS_FORBIDDEN
                                : ErrorCode.SERVICE_NOT_FOUND));
        QueueConfiguration queue = queueRepository
                .findByBusinessServiceIdForUpdate(service.getId())
                .orElseGet(() -> createLegacyQueue(context, service));
        return createLocked(
                queue,
                new CreateQueueSessionForQueueRequest(
                        request.openingTime(), request.closingTime()));
    }

    @Transactional
    public QueueSessionResponse createToday(
            UUID queueId, CreateQueueSessionForQueueRequest request) {
        AuthenticatedBusinessContext context = contextService.current();
        QueueConfiguration queue = queueRepository
                .findByIdAndBusinessIdForUpdate(queueId, context.business().getId())
                .orElseThrow(() -> new ApplicationException(
                        queueRepository.existsById(queueId)
                                ? ErrorCode.CROSS_BUSINESS_ACCESS_FORBIDDEN
                                : ErrorCode.QUEUE_NOT_FOUND));
        return createLocked(queue, request);
    }

    @Transactional
    public QueueSessionResponse open(UUID sessionId) {
        return transition(sessionId, session -> {
            ensureActiveQueue(session.getQueue());
            session.open();
        });
    }

    @Transactional
    public QueueSessionResponse pause(UUID sessionId) {
        return transition(sessionId, QueueSession::pause);
    }

    @Transactional
    public QueueSessionResponse resume(UUID sessionId) {
        return transition(sessionId, session -> {
            ensureActiveQueue(session.getQueue());
            session.resume();
        });
    }

    @Transactional
    public QueueSessionResponse close(UUID sessionId) {
        return transition(sessionId, session -> {
            if (session.getStatus() == com.syntagi.queue.enums.QueueSessionStatus.CLOSED) {
                throw new ApplicationException(
                        ErrorCode.QUEUE_SESSION_CLOSED, "Queue session is already closed");
            }
            session.close(timeService.nowOffset());
        });
    }

    private QueueSessionResponse createLocked(
            QueueConfiguration queue, CreateQueueSessionForQueueRequest request) {
        ensureActiveQueue(queue);
        BusinessService service = queue.getBusinessService();
        if (!service.isActive()) throw new ApplicationException(ErrorCode.SERVICE_INACTIVE);
        if (!service.supportsWalkIn()) {
            throw new ApplicationException(ErrorCode.WALK_IN_NOT_SUPPORTED);
        }
        LocalDate businessDate = timeService.businessDate(queue.getBusiness());
        if (sessionRepository.existsByQueueIdAndBusinessDate(queue.getId(), businessDate)) {
            throw new ApplicationException(ErrorCode.DUPLICATE_QUEUE_SESSION);
        }
        if (sessionRepository.existsActiveByQueueId(queue.getId())) {
            throw new ApplicationException(ErrorCode.ACTIVE_QUEUE_SESSION_EXISTS);
        }
        ServiceSchedule schedule = todaySchedule(service, businessDate);
        LocalTime openingTime = request.openingTime() != null
                ? request.openingTime()
                : schedule == null
                        ? timeService.businessTime(queue.getBusiness())
                        : schedule.getOperatingStartTime();
        LocalTime closingTime = request.closingTime() != null
                ? request.closingTime()
                : schedule == null ? null : schedule.getOperatingEndTime();
        // A closing time before the opening time represents an overnight queue
        // (for example, 21:00 to 03:00). Equal times remain invalid because the
        // request does not carry enough information to distinguish zero hours
        // from a full 24-hour window.
        if (closingTime != null && closingTime.equals(openingTime)) {
            throw new ApplicationException(ErrorCode.INVALID_TIME_RANGE);
        }
        QueueSession session = sessionRepository.saveAndFlush(new QueueSession(
                queue,
                queue.getBusiness(),
                service,
                request.openingTime() == null && request.closingTime() == null ? schedule : null,
                businessDate,
                openingTime,
                closingTime,
                timeService.nowOffset()));
        appointmentTokenCoordinator.generateFor(session);
        return response(session);
    }

    private QueueSessionResponse transition(UUID sessionId, Consumer<QueueSession> operation) {
        AuthenticatedBusinessContext context = contextService.current();
        QueueSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.QUEUE_SESSION_NOT_FOUND));
        if (!session.getBusiness().getId().equals(context.business().getId())) {
            throw new ApplicationException(ErrorCode.CROSS_BUSINESS_ACCESS_FORBIDDEN);
        }
        try {
            operation.accept(session);
        } catch (com.syntagi.common.exception.InvalidEntityStateException exception) {
            throw new ApplicationException(ErrorCode.INVALID_QUEUE_TRANSITION, exception.getMessage());
        }
        return response(session);
    }

    private QueueConfiguration createLegacyQueue(
            AuthenticatedBusinessContext context, BusinessService service) {
        contextService.requireOwner();
        if (!service.isActive()) throw new ApplicationException(ErrorCode.SERVICE_INACTIVE);
        if (!service.supportsWalkIn()) {
            throw new ApplicationException(ErrorCode.WALK_IN_NOT_SUPPORTED);
        }
        QueueConfiguration queue = new QueueConfiguration(
                context.business(), service, service.getName());
        queue.activate();
        return queueRepository.saveAndFlush(queue);
    }

    private ServiceSchedule todaySchedule(BusinessService service, LocalDate businessDate) {
        return scheduleRepository.findByBusinessServiceIdAndActiveTrue(service.getId()).stream()
                .filter(schedule -> schedule.getDayOfWeek() == businessDate.getDayOfWeek())
                .filter(ServiceSchedule::isWalkInEnabled)
                .min(Comparator.comparing(ServiceSchedule::getOperatingStartTime))
                .orElse(null);
    }

    private static void ensureActiveQueue(QueueConfiguration queue) {
        if (!queue.isActive()) throw new ApplicationException(ErrorCode.QUEUE_NOT_ACTIVE);
    }

    private static QueueSessionResponse response(QueueSession session) {
        return new QueueSessionResponse(
                session.getId(),
                session.getQueue().getId(),
                session.getQueue().getName(),
                session.getBusinessService().getId(),
                session.getBusinessService().getName(),
                session.getBusinessDate(),
                session.getStatus(),
                session.getOpeningTime(),
                session.getClosingTime(),
                session.getOpenedAt(),
                session.getClosedAt());
    }
}
