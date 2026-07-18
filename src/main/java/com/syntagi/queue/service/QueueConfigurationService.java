package com.syntagi.queue.service;

import com.syntagi.auth.service.AuthenticatedBusinessContext;
import com.syntagi.auth.service.AuthenticatedBusinessContextService;
import com.syntagi.common.exception.ApplicationException;
import com.syntagi.common.exception.ErrorCode;
import com.syntagi.queue.dto.request.QueueUpsertRequest;
import com.syntagi.queue.dto.response.QueueConfigurationResponse;
import com.syntagi.queue.entity.QueueConfiguration;
import com.syntagi.queue.enums.QueueStatus;
import com.syntagi.queue.repository.QueueConfigurationRepository;
import com.syntagi.queue.repository.QueueSessionRepository;
import com.syntagi.servicecatalog.entity.BusinessService;
import com.syntagi.servicecatalog.repository.BusinessServiceRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueueConfigurationService {

    private final AuthenticatedBusinessContextService contextService;
    private final QueueConfigurationRepository queueRepository;
    private final QueueSessionRepository sessionRepository;
    private final BusinessServiceRepository serviceRepository;

    public QueueConfigurationService(
            AuthenticatedBusinessContextService contextService,
            QueueConfigurationRepository queueRepository,
            QueueSessionRepository sessionRepository,
            BusinessServiceRepository serviceRepository) {
        this.contextService = contextService;
        this.queueRepository = queueRepository;
        this.sessionRepository = sessionRepository;
        this.serviceRepository = serviceRepository;
    }

    @Transactional
    public QueueConfigurationResponse create(QueueUpsertRequest request) {
        AuthenticatedBusinessContext context = contextService.requireOwner();
        BusinessService service = serviceRepository.findByIdAndBusinessIdForUpdate(
                        request.serviceId(), context.business().getId())
                .orElseThrow(() -> new ApplicationException(
                        serviceRepository.existsById(request.serviceId())
                                ? ErrorCode.CROSS_BUSINESS_ACCESS_FORBIDDEN
                                : ErrorCode.SERVICE_NOT_FOUND));
        validateService(service);
        if (queueRepository.existsByBusinessIdAndBusinessServiceId(
                context.business().getId(), service.getId())) {
            throw new ApplicationException(ErrorCode.DUPLICATE_QUEUE);
        }
        return response(queueRepository.saveAndFlush(
                new QueueConfiguration(context.business(), service, request.name())));
    }

    @Transactional(readOnly = true)
    public List<QueueConfigurationResponse> list() {
        UUID businessId = contextService.current().business().getId();
        return queueRepository
                .findByBusinessIdAndStatusNotOrderByNameAsc(businessId, QueueStatus.ARCHIVED)
                .stream().map(QueueConfigurationService::response).toList();
    }

    @Transactional(readOnly = true)
    public QueueConfigurationResponse get(UUID queueId) {
        UUID businessId = contextService.current().business().getId();
        return response(findScoped(queueId, businessId));
    }

    public Map<String, String> persistenceDiagnostic() {
        contextService.requireOwner();
        try {
            queueRepository.count();
            return Map.of("status", "OK");
        } catch (RuntimeException exception) {
            Throwable root = exception;
            while (root.getCause() != null) root = root.getCause();
            return Map.of(
                    "exception", exception.getClass().getName(),
                    "rootException", root.getClass().getName(),
                    "rootMessage", String.valueOf(root.getMessage()));
        }
    }

    @Transactional
    public QueueConfigurationResponse update(UUID queueId, QueueUpsertRequest request) {
        AuthenticatedBusinessContext context = contextService.requireOwner();
        QueueConfiguration queue = lockScoped(queueId, context.business().getId());
        if (!queue.getBusinessService().getId().equals(request.serviceId())) {
            throw new ApplicationException(
                    ErrorCode.INVALID_QUEUE_TRANSITION,
                    "Queue service cannot be changed after creation");
        }
        queue.rename(request.name());
        return response(queue);
    }

    @Transactional
    public QueueConfigurationResponse activate(UUID queueId) {
        return transition(queueId, QueueConfiguration::activate, false);
    }

    @Transactional
    public QueueConfigurationResponse pause(UUID queueId) {
        return transition(queueId, QueueConfiguration::pause, true);
    }

    @Transactional
    public QueueConfigurationResponse close(UUID queueId) {
        return transition(queueId, QueueConfiguration::close, true);
    }

    @Transactional
    public QueueConfigurationResponse archive(UUID queueId) {
        return transition(queueId, QueueConfiguration::archive, true);
    }

    private QueueConfigurationResponse transition(
            UUID queueId, Consumer<QueueConfiguration> operation, boolean requireNoActiveSession) {
        UUID businessId = contextService.requireOwner().business().getId();
        QueueConfiguration queue = lockScoped(queueId, businessId);
        if (requireNoActiveSession && sessionRepository.existsActiveByQueueId(queueId)) {
            throw new ApplicationException(ErrorCode.ACTIVE_QUEUE_SESSION_EXISTS);
        }
        try {
            operation.accept(queue);
        } catch (com.syntagi.common.exception.InvalidEntityStateException exception) {
            throw new ApplicationException(ErrorCode.INVALID_QUEUE_TRANSITION, exception.getMessage());
        }
        return response(queue);
    }

    private BusinessService ownedService(UUID serviceId, UUID businessId) {
        return serviceRepository.findByIdAndBusinessId(serviceId, businessId)
                .orElseThrow(() -> new ApplicationException(
                        serviceRepository.existsById(serviceId)
                                ? ErrorCode.CROSS_BUSINESS_ACCESS_FORBIDDEN
                                : ErrorCode.SERVICE_NOT_FOUND));
    }

    private static void validateService(BusinessService service) {
        if (!service.isActive()) throw new ApplicationException(ErrorCode.SERVICE_INACTIVE);
        if (!service.supportsWalkIn()) {
            throw new ApplicationException(ErrorCode.WALK_IN_NOT_SUPPORTED);
        }
    }

    private QueueConfiguration findScoped(UUID queueId, UUID businessId) {
        return queueRepository.findByIdAndBusinessId(queueId, businessId)
                .orElseThrow(() -> new ApplicationException(
                        queueRepository.existsById(queueId)
                                ? ErrorCode.CROSS_BUSINESS_ACCESS_FORBIDDEN
                                : ErrorCode.QUEUE_NOT_FOUND));
    }

    private QueueConfiguration lockScoped(UUID queueId, UUID businessId) {
        return queueRepository.findByIdAndBusinessIdForUpdate(queueId, businessId)
                .orElseThrow(() -> new ApplicationException(
                        queueRepository.existsById(queueId)
                                ? ErrorCode.CROSS_BUSINESS_ACCESS_FORBIDDEN
                                : ErrorCode.QUEUE_NOT_FOUND));
    }

    private static QueueConfigurationResponse response(QueueConfiguration queue) {
        return new QueueConfigurationResponse(
                queue.getId(),
                queue.getBusinessService().getId(),
                queue.getBusinessService().getName(),
                queue.getName(),
                queue.getStatus(),
                queue.getCreatedAt(),
                queue.getUpdatedAt(),
                queue.getVersion());
    }
}
