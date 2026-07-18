package com.syntagi.servicecatalog.service;

import com.syntagi.business.entity.Business;
import com.syntagi.business.enums.BusinessStatus;
import com.syntagi.business.repository.BusinessRepository;
import com.syntagi.common.exception.ApplicationException;
import com.syntagi.common.exception.ErrorCode;
import com.syntagi.servicecatalog.dto.response.PublicServiceResponse;
import com.syntagi.servicecatalog.dto.response.PublicBusinessResponse;
import com.syntagi.queue.entity.QueueSession;
import com.syntagi.queue.enums.QueueSessionStatus;
import com.syntagi.queue.repository.QueueSessionRepository;
import com.syntagi.queue.service.QueueTimeService;
import com.syntagi.servicecatalog.mapper.ServiceCatalogMapper;
import com.syntagi.servicecatalog.repository.BusinessServiceRepository;
import com.syntagi.servicecatalog.entity.BusinessService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicServiceQueryService {

    private final BusinessRepository businessRepository;
    private final BusinessServiceRepository serviceRepository;
    private final ServiceCatalogMapper mapper;
    private final QueueSessionRepository queueSessionRepository;
    private final QueueTimeService queueTimeService;

    public PublicServiceQueryService(
            BusinessRepository businessRepository,
            BusinessServiceRepository serviceRepository,
            ServiceCatalogMapper mapper,
            QueueSessionRepository queueSessionRepository,
            QueueTimeService queueTimeService) {
        this.businessRepository = businessRepository;
        this.serviceRepository = serviceRepository;
        this.mapper = mapper;
        this.queueSessionRepository = queueSessionRepository;
        this.queueTimeService = queueTimeService;
    }

    @Transactional(readOnly = true)
    public PublicBusinessResponse business(String publicQueueCode) {
        Business business = activeBusiness(publicQueueCode);
        List<PublicServiceResponse> services = activeServices(business);
        QueueSessionStatus queueStatus = services.stream()
                .anyMatch(service -> service.queueStatus() == QueueSessionStatus.OPEN)
                ? QueueSessionStatus.OPEN : QueueSessionStatus.CLOSED;
        return new PublicBusinessResponse(
                business.getName(), business.getBusinessType(), queueStatus, services);
    }

    private Business activeBusiness(String publicQueueCode) {
        return businessRepository.findByPublicQueueCode(publicQueueCode)
                .filter(candidate -> candidate.getStatus() == BusinessStatus.ACTIVE)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private List<PublicServiceResponse> activeServices(Business business) {
        List<BusinessService> services = serviceRepository
                .findByBusinessIdAndActiveTrueOrderByDisplayOrderAscNameAsc(business.getId())
                .stream().toList();
        Map<UUID, QueueSession> sessionsByService = queueSessionRepository
                .findByBusinessIdAndBusinessDate(
                        business.getId(), queueTimeService.businessDate(business))
                .stream()
                .collect(Collectors.toMap(
                        session -> session.getBusinessService().getId(),
                        Function.identity()));
        return services.stream()
                .map(service -> {
                    QueueSession session = sessionsByService.get(service.getId());
                    QueueSessionStatus status = session == null
                            ? QueueSessionStatus.CLOSED
                            : session.getStatus();
                    return mapper.toPublicServiceResponse(service, status);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublicServiceResponse> listActiveServices(String publicQueueCode) {
        return activeServices(activeBusiness(publicQueueCode));
    }
}
