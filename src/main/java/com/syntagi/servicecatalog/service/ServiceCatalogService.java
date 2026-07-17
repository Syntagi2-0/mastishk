package com.syntagi.servicecatalog.service;

import com.syntagi.auth.service.AuthenticatedBusinessContext;
import com.syntagi.auth.service.AuthenticatedBusinessContextService;
import com.syntagi.common.exception.ApplicationException;
import com.syntagi.common.exception.ErrorCode;
import com.syntagi.servicecatalog.dto.request.ServiceStatusRequest;
import com.syntagi.servicecatalog.dto.request.ServiceUpsertRequest;
import com.syntagi.servicecatalog.dto.response.ServiceResponse;
import com.syntagi.servicecatalog.entity.BusinessService;
import com.syntagi.servicecatalog.entity.ServiceSchedule;
import com.syntagi.servicecatalog.enums.ServiceMode;
import com.syntagi.servicecatalog.mapper.ServiceCatalogMapper;
import com.syntagi.servicecatalog.repository.BusinessServiceRepository;
import com.syntagi.servicecatalog.repository.ServiceScheduleRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@Service
public class ServiceCatalogService {

    private final AuthenticatedBusinessContextService contextService;
    private final BusinessServiceRepository serviceRepository;
    private final ServiceScheduleRepository scheduleRepository;
    private final ServiceCatalogMapper mapper;

    public ServiceCatalogService(
            AuthenticatedBusinessContextService contextService,
            BusinessServiceRepository serviceRepository,
            ServiceScheduleRepository scheduleRepository,
            ServiceCatalogMapper mapper) {
        this.contextService = contextService;
        this.serviceRepository = serviceRepository;
        this.scheduleRepository = scheduleRepository;
        this.mapper = mapper;
    }

    @Transactional
    public ServiceResponse create(ServiceUpsertRequest request) {
        AuthenticatedBusinessContext context = contextService.requireOwner();
        validateDefinition(request);
        validateUniqueness(context.business().getId(), request, null);

        BusinessService service = new BusinessService(
                context.business(), request.name(), request.serviceCode(), request.serviceMode());
        service.updateDefinition(
                request.name(),
                request.description(),
                request.serviceCode(),
                request.serviceMode(),
                request.expectedDurationMinutes(),
                request.appointmentSlotDurationMinutes(),
                request.displayOrder());
        return mapper.toServiceResponse(serviceRepository.saveAndFlush(service));
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> list(Boolean active) {
        UUID businessId = contextService.current().business().getId();
        boolean requestedStatus = active == null || active;
        return serviceRepository
                .findByBusinessIdAndActiveOrderByDisplayOrderAscNameAsc(
                        businessId, requestedStatus)
                .stream()
                .map(mapper::toServiceResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ServiceResponse> search(
            String search, Boolean active, ServiceMode serviceMode, Pageable pageable) {
        UUID businessId = contextService.current().business().getId();
        Specification<BusinessService> filters = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("business").get("id"), businessId));
            if (active != null) predicates.add(cb.equal(root.get("active"), active));
            if (serviceMode != null) predicates.add(cb.equal(root.get("serviceMode"), serviceMode));
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("serviceCode")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern)));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Pageable safePage = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by("displayOrder").ascending().and(Sort.by("name").ascending()));
        return serviceRepository.findAll(filters, safePage).map(mapper::toServiceResponse);
    }

    @Transactional(readOnly = true)
    public ServiceResponse get(UUID serviceId) {
        UUID businessId = contextService.current().business().getId();
        return mapper.toServiceResponse(findScopedService(serviceId, businessId));
    }

    @Transactional
    public ServiceResponse update(UUID serviceId, ServiceUpsertRequest request) {
        AuthenticatedBusinessContext context = contextService.requireOwner();
        BusinessService service = findScopedService(serviceId, context.business().getId());
        validateDefinition(request);
        validateUniqueness(context.business().getId(), request, serviceId);
        validateExistingSchedulesForMode(service, request.serviceMode());

        service.updateDefinition(
                request.name(),
                request.description(),
                request.serviceCode(),
                request.serviceMode(),
                request.expectedDurationMinutes(),
                request.appointmentSlotDurationMinutes(),
                request.displayOrder());
        return mapper.toServiceResponse(service);
    }

    @Transactional
    public ServiceResponse updateStatus(UUID serviceId, ServiceStatusRequest request) {
        AuthenticatedBusinessContext context = contextService.requireOwner();
        BusinessService service = findScopedService(serviceId, context.business().getId());
        if (request.active()) {
            service.activate();
        } else {
            service.deactivate();
        }
        return mapper.toServiceResponse(service);
    }

    BusinessService findScopedService(UUID serviceId, UUID businessId) {
        return serviceRepository.findByIdAndBusinessId(serviceId, businessId)
                .orElseThrow(() -> new ApplicationException(
                        serviceRepository.existsById(serviceId)
                                ? ErrorCode.CROSS_BUSINESS_ACCESS_FORBIDDEN
                                : ErrorCode.SERVICE_NOT_FOUND));
    }

    static void validateModeCompatibility(
            ServiceMode serviceMode,
            boolean appointmentBookingEnabled,
            boolean walkInEnabled) {
        if ((serviceMode == ServiceMode.WALK_IN && appointmentBookingEnabled)
                || (serviceMode == ServiceMode.APPOINTMENT && walkInEnabled)) {
            throw new ApplicationException(ErrorCode.INCOMPATIBLE_SERVICE_MODE);
        }
    }

    private void validateExistingSchedulesForMode(
            BusinessService service, ServiceMode requestedMode) {
        for (ServiceSchedule schedule :
                scheduleRepository.findByBusinessServiceIdAndActiveTrue(service.getId())) {
            validateModeCompatibility(
                    requestedMode,
                    schedule.isAppointmentBookingEnabled(),
                    schedule.isWalkInEnabled());
        }
    }

    private void validateUniqueness(
            UUID businessId, ServiceUpsertRequest request, UUID excludedServiceId) {
        String serviceCode = request.serviceCode().toUpperCase(Locale.ROOT);
        boolean duplicateCode = excludedServiceId == null
                ? serviceRepository.existsByBusinessIdAndServiceCodeIgnoreCase(
                        businessId, serviceCode)
                : serviceRepository.existsByBusinessIdAndServiceCodeIgnoreCaseAndIdNot(
                        businessId, serviceCode, excludedServiceId);
        if (duplicateCode) {
            throw new ApplicationException(ErrorCode.DUPLICATE_SERVICE_CODE);
        }

        boolean duplicateName = excludedServiceId == null
                ? serviceRepository.existsByBusinessIdAndNameIgnoreCase(
                        businessId, request.name())
                : serviceRepository.existsByBusinessIdAndNameIgnoreCaseAndIdNot(
                        businessId, request.name(), excludedServiceId);
        if (duplicateName) {
            throw new ApplicationException(ErrorCode.DUPLICATE_SERVICE_NAME);
        }
    }

    private static void validateDefinition(ServiceUpsertRequest request) {
        if (request.expectedDurationMinutes() == null || request.expectedDurationMinutes() <= 0
                || (request.appointmentSlotDurationMinutes() != null
                        && request.appointmentSlotDurationMinutes() <= 0)) {
            throw new ApplicationException(
                    ErrorCode.VALIDATION_FAILED, "Service durations must be greater than zero");
        }
        if ((request.serviceMode() == ServiceMode.APPOINTMENT
                        || request.serviceMode() == ServiceMode.BOTH)
                && request.appointmentSlotDurationMinutes() == null) {
            throw new ApplicationException(ErrorCode.APPOINTMENT_DURATION_REQUIRED);
        }
    }
}
