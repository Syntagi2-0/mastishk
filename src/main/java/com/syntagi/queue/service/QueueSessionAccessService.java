package com.syntagi.queue.service;

import com.syntagi.business.entity.Business;
import com.syntagi.common.exception.ApplicationException;
import com.syntagi.common.exception.ErrorCode;
import com.syntagi.queue.entity.QueueSession;
import com.syntagi.queue.repository.QueueSessionRepository;
import com.syntagi.servicecatalog.entity.BusinessService;
import com.syntagi.servicecatalog.repository.BusinessServiceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class QueueSessionAccessService {

    private final QueueSessionRepository sessionRepository;
    private final BusinessServiceRepository serviceRepository;
    private final QueueTimeService timeService;

    public QueueSessionAccessService(
            QueueSessionRepository sessionRepository,
            BusinessServiceRepository serviceRepository,
            QueueTimeService timeService) {
        this.sessionRepository = sessionRepository;
        this.serviceRepository = serviceRepository;
        this.timeService = timeService;
    }

    public QueueSession findToday(Business business, UUID serviceId) {
        LocalDate businessDate = timeService.businessDate(business);
        if (serviceId != null) {
            requireOwnedService(business, serviceId);
            return sessionRepository
                    .findByBusinessIdAndBusinessServiceIdAndBusinessDate(
                            business.getId(), serviceId, businessDate)
                    .orElseThrow(() -> new ApplicationException(ErrorCode.QUEUE_SESSION_NOT_FOUND));
        }
        return selectSingle(sessionRepository.findByBusinessIdAndBusinessDate(
                business.getId(), businessDate));
    }

    public QueueSession lockToday(Business business, UUID serviceId) {
        LocalDate businessDate = timeService.businessDate(business);
        UUID selectedServiceId = serviceId;
        if (selectedServiceId == null) {
            selectedServiceId = selectSingleServiceId(
                    sessionRepository.findServiceIdsByBusinessIdAndBusinessDate(
                            business.getId(), businessDate));
        } else {
            requireOwnedService(business, selectedServiceId);
        }
        return sessionRepository.findTodayForUpdate(
                        business.getId(), selectedServiceId, businessDate)
                .orElseThrow(() -> new ApplicationException(ErrorCode.QUEUE_SESSION_NOT_FOUND));
    }

    private BusinessService requireOwnedService(Business business, UUID serviceId) {
        BusinessService service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.SERVICE_NOT_FOUND));
        if (!service.getBusiness().getId().equals(business.getId())) {
            throw new ApplicationException(ErrorCode.CROSS_BUSINESS_ACCESS_FORBIDDEN);
        }
        return service;
    }

    private static QueueSession selectSingle(List<QueueSession> sessions) {
        if (sessions.isEmpty()) {
            throw new ApplicationException(ErrorCode.QUEUE_SESSION_NOT_FOUND);
        }
        if (sessions.size() > 1) {
            throw new ApplicationException(ErrorCode.QUEUE_SERVICE_REQUIRED);
        }
        return sessions.getFirst();
    }

    private static UUID selectSingleServiceId(List<UUID> serviceIds) {
        if (serviceIds.isEmpty()) {
            throw new ApplicationException(ErrorCode.QUEUE_SESSION_NOT_FOUND);
        }
        if (serviceIds.size() > 1) {
            throw new ApplicationException(ErrorCode.QUEUE_SERVICE_REQUIRED);
        }
        return serviceIds.getFirst();
    }
}
