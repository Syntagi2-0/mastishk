package com.syntagi.servicecatalog.service;

import com.syntagi.auth.service.AuthenticatedBusinessContext;
import com.syntagi.auth.service.AuthenticatedBusinessContextService;
import com.syntagi.common.exception.ApplicationException;
import com.syntagi.common.exception.ErrorCode;
import com.syntagi.servicecatalog.dto.request.ScheduleStatusRequest;
import com.syntagi.servicecatalog.dto.request.ScheduleUpsertRequest;
import com.syntagi.servicecatalog.dto.response.ScheduleResponse;
import com.syntagi.servicecatalog.entity.BusinessService;
import com.syntagi.servicecatalog.entity.ServiceSchedule;
import com.syntagi.servicecatalog.mapper.ServiceCatalogMapper;
import com.syntagi.servicecatalog.repository.ServiceScheduleRepository;
import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceScheduleService {

    private final AuthenticatedBusinessContextService contextService;
    private final ServiceCatalogService serviceCatalogService;
    private final ServiceScheduleRepository scheduleRepository;
    private final ServiceCatalogMapper mapper;

    public ServiceScheduleService(
            AuthenticatedBusinessContextService contextService,
            ServiceCatalogService serviceCatalogService,
            ServiceScheduleRepository scheduleRepository,
            ServiceCatalogMapper mapper) {
        this.contextService = contextService;
        this.serviceCatalogService = serviceCatalogService;
        this.scheduleRepository = scheduleRepository;
        this.mapper = mapper;
    }

    @Transactional
    public ScheduleResponse create(UUID serviceId, ScheduleUpsertRequest request) {
        AuthenticatedBusinessContext context = contextService.requireOwner();
        BusinessService service = serviceCatalogService.findScopedService(
                serviceId, context.business().getId());
        validateRequest(service, request);
        validateNoOverlap(serviceId, request, null);

        ServiceSchedule schedule = new ServiceSchedule(
                service,
                DayOfWeek.of(request.dayOfWeek()),
                request.operatingStartTime(),
                request.operatingEndTime());
        schedule.updateConfiguration(
                DayOfWeek.of(request.dayOfWeek()),
                request.operatingStartTime(),
                request.operatingEndTime(),
                request.queueOpenBeforeMinutes(),
                request.appointmentBookingEnabled(),
                request.walkInEnabled());
        return mapper.toScheduleResponse(scheduleRepository.saveAndFlush(schedule));
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> list(UUID serviceId) {
        UUID businessId = contextService.current().business().getId();
        serviceCatalogService.findScopedService(serviceId, businessId);
        return scheduleRepository
                .findByBusinessServiceIdOrderByDayOfWeekAscOperatingStartTimeAsc(serviceId)
                .stream()
                .map(mapper::toScheduleResponse)
                .toList();
    }

    @Transactional
    public ScheduleResponse update(
            UUID serviceId, UUID scheduleId, ScheduleUpsertRequest request) {
        AuthenticatedBusinessContext context = contextService.requireOwner();
        BusinessService service = serviceCatalogService.findScopedService(
                serviceId, context.business().getId());
        ServiceSchedule schedule = findScopedSchedule(scheduleId, serviceId);
        validateRequest(service, request);
        if (schedule.isActive()) {
            validateNoOverlap(serviceId, request, scheduleId);
        }

        schedule.updateConfiguration(
                DayOfWeek.of(request.dayOfWeek()),
                request.operatingStartTime(),
                request.operatingEndTime(),
                request.queueOpenBeforeMinutes(),
                request.appointmentBookingEnabled(),
                request.walkInEnabled());
        return mapper.toScheduleResponse(schedule);
    }

    @Transactional
    public ScheduleResponse updateStatus(
            UUID serviceId, UUID scheduleId, ScheduleStatusRequest request) {
        AuthenticatedBusinessContext context = contextService.requireOwner();
        BusinessService service = serviceCatalogService.findScopedService(
                serviceId, context.business().getId());
        ServiceSchedule schedule = findScopedSchedule(scheduleId, serviceId);
        if (request.active()) {
            ServiceCatalogService.validateModeCompatibility(
                    service.getServiceMode(),
                    schedule.isAppointmentBookingEnabled(),
                    schedule.isWalkInEnabled());
            ScheduleUpsertRequest currentConfiguration = new ScheduleUpsertRequest(
                    schedule.getDayOfWeek().getValue(),
                    schedule.getOperatingStartTime(),
                    schedule.getOperatingEndTime(),
                    schedule.getQueueOpenBeforeMinutes(),
                    schedule.isAppointmentBookingEnabled(),
                    schedule.isWalkInEnabled());
            validateNoOverlap(serviceId, currentConfiguration, scheduleId);
            schedule.activate();
        } else {
            schedule.deactivate();
        }
        return mapper.toScheduleResponse(schedule);
    }

    private ServiceSchedule findScopedSchedule(UUID scheduleId, UUID serviceId) {
        return scheduleRepository.findByIdAndBusinessServiceId(scheduleId, serviceId)
                .orElseThrow(() -> new ApplicationException(
                        scheduleRepository.existsById(scheduleId)
                                ? ErrorCode.CROSS_BUSINESS_ACCESS_FORBIDDEN
                                : ErrorCode.SCHEDULE_NOT_FOUND));
    }

    private void validateNoOverlap(
            UUID serviceId, ScheduleUpsertRequest request, UUID excludedScheduleId) {
        boolean overlaps = scheduleRepository
                .findActiveOverlaps(
                        serviceId,
                        DayOfWeek.of(request.dayOfWeek()),
                        request.operatingStartTime(),
                        request.operatingEndTime())
                .stream()
                .anyMatch(schedule -> !schedule.getId().equals(excludedScheduleId));
        if (overlaps) {
            throw new ApplicationException(ErrorCode.SCHEDULE_OVERLAP);
        }
    }

    private static void validateRequest(
            BusinessService service, ScheduleUpsertRequest request) {
        if (request.dayOfWeek() == null || request.dayOfWeek() < 1 || request.dayOfWeek() > 7
                || request.queueOpenBeforeMinutes() == null
                || request.queueOpenBeforeMinutes() < 0
                || request.queueOpenBeforeMinutes() > 1440) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        if (request.operatingStartTime() == null
                || request.operatingEndTime() == null
                || !request.operatingEndTime().isAfter(request.operatingStartTime())) {
            throw new ApplicationException(ErrorCode.INVALID_TIME_RANGE);
        }
        ServiceCatalogService.validateModeCompatibility(
                service.getServiceMode(),
                request.appointmentBookingEnabled(),
                request.walkInEnabled());
    }
}
