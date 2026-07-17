package com.syntagi.appointment.service;

import com.syntagi.appointment.dto.request.GenerateSlotsRequest;
import com.syntagi.appointment.dto.response.AppointmentSlotResponse;
import com.syntagi.appointment.dto.response.SlotGenerationResponse;
import com.syntagi.appointment.entity.AppointmentSlot;
import com.syntagi.appointment.enums.AppointmentSlotStatus;
import com.syntagi.appointment.enums.AppointmentStatus;
import com.syntagi.appointment.repository.AppointmentRepository;
import com.syntagi.appointment.repository.AppointmentSlotRepository;
import com.syntagi.auth.service.AuthenticatedBusinessContext;
import com.syntagi.auth.service.AuthenticatedBusinessContextService;
import com.syntagi.common.exception.ApplicationException;
import com.syntagi.common.exception.ErrorCode;
import com.syntagi.servicecatalog.entity.BusinessService;
import com.syntagi.servicecatalog.entity.ServiceSchedule;
import com.syntagi.servicecatalog.repository.BusinessServiceRepository;
import com.syntagi.servicecatalog.repository.ServiceScheduleRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentSlotService {

    private final AuthenticatedBusinessContextService contextService;
    private final BusinessServiceRepository serviceRepository;
    private final ServiceScheduleRepository scheduleRepository;
    private final AppointmentSlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentResponseMapper mapper;

    public AppointmentSlotService(
            AuthenticatedBusinessContextService contextService,
            BusinessServiceRepository serviceRepository,
            ServiceScheduleRepository scheduleRepository,
            AppointmentSlotRepository slotRepository,
            AppointmentRepository appointmentRepository,
            AppointmentResponseMapper mapper) {
        this.contextService = contextService;
        this.serviceRepository = serviceRepository;
        this.scheduleRepository = scheduleRepository;
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
        this.mapper = mapper;
    }

    @Transactional
    public SlotGenerationResponse generate(GenerateSlotsRequest request) {
        AuthenticatedBusinessContext context = contextService.requireOwner();
        if (request.endDate().isBefore(request.startDate())) {
            throw new ApplicationException(ErrorCode.INVALID_TIME_RANGE,
                    "endDate must be on or after startDate");
        }
        BusinessService service = serviceRepository.findByIdAndBusinessIdForUpdate(
                        request.serviceId(), context.business().getId())
                .orElseThrow(() -> serviceRepository.existsById(request.serviceId())
                        ? new ApplicationException(ErrorCode.CROSS_BUSINESS_ACCESS_FORBIDDEN)
                        : new ApplicationException(ErrorCode.SERVICE_NOT_FOUND));
        requireAppointmentSupport(service);
        int duration = service.getAppointmentSlotDurationMinutes();
        List<ServiceSchedule> schedules = scheduleRepository
                .findByBusinessServiceIdAndActiveTrue(service.getId()).stream()
                .filter(ServiceSchedule::isAppointmentBookingEnabled)
                .toList();
        int generated = 0;
        int duplicates = 0;
        for (LocalDate date = request.startDate(); !date.isAfter(request.endDate()); date = date.plusDays(1)) {
            for (ServiceSchedule schedule : schedules) {
                if (schedule.getDayOfWeek() != date.getDayOfWeek()) {
                    continue;
                }
                LocalTime start = schedule.getOperatingStartTime();
                while (!start.plusMinutes(duration).isAfter(schedule.getOperatingEndTime())) {
                    if (slotRepository.existsByBusinessServiceIdAndSlotDateAndStartTime(
                            service.getId(), date, start)) {
                        duplicates++;
                    } else {
                        slotRepository.save(new AppointmentSlot(
                                context.business(), service, date, start,
                                start.plusMinutes(duration), 1));
                        generated++;
                    }
                    start = start.plusMinutes(duration);
                }
            }
        }
        if (generated == 0 && duplicates > 0) {
            throw new ApplicationException(ErrorCode.DUPLICATE_SLOT_GENERATION);
        }
        slotRepository.flush();
        return new SlotGenerationResponse(generated, duplicates);
    }

    @Transactional(readOnly = true)
    public List<AppointmentSlotResponse> list(
            UUID serviceId, LocalDate date, AppointmentSlotStatus status) {
        AuthenticatedBusinessContext context = contextService.current();
        if (serviceId != null) {
            ownedService(serviceId, context.business().getId());
        }
        List<AppointmentSlot> slots;
        if (serviceId != null && date != null) {
            slots = slotRepository.findByBusinessIdAndBusinessServiceIdAndSlotDateOrderByStartTimeAsc(
                    context.business().getId(), serviceId, date);
        } else if (date != null) {
            slots = slotRepository.findByBusinessIdAndSlotDateOrderByStartTimeAsc(
                    context.business().getId(), date);
        } else {
            slots = slotRepository.findByBusinessIdOrderBySlotDateAscStartTimeAsc(
                    context.business().getId());
        }
        return slots.stream()
                .filter(slot -> serviceId == null || slot.getBusinessService().getId().equals(serviceId))
                .filter(slot -> status == null || slot.getStatus() == status)
                .map(mapper::slot)
                .toList();
    }

    @Transactional
    public AppointmentSlotResponse updateStatus(UUID slotId, AppointmentSlotStatus status) {
        AuthenticatedBusinessContext context = contextService.requireOwner();
        AppointmentSlot slot = slotRepository.findByIdAndBusinessIdForUpdate(
                        slotId, context.business().getId())
                .orElseThrow(() -> crossBusinessOrNotFound(slotId));
        if (status != AppointmentSlotStatus.AVAILABLE
                && appointmentRepository.countByAppointmentSlotIdAndStatus(
                        slotId, AppointmentStatus.CONFIRMED) > 0) {
            throw new ApplicationException(ErrorCode.SLOT_HAS_CONFIRMED_BOOKINGS);
        }
        switch (status) {
            case AVAILABLE -> slot.makeAvailable();
            case BLOCKED -> slot.block();
            case CLOSED -> slot.close();
        }
        return mapper.slot(slot);
    }

    private BusinessService ownedService(UUID id, UUID businessId) {
        return serviceRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> serviceRepository.existsById(id)
                        ? new ApplicationException(ErrorCode.CROSS_BUSINESS_ACCESS_FORBIDDEN)
                        : new ApplicationException(ErrorCode.SERVICE_NOT_FOUND));
    }

    private ApplicationException crossBusinessOrNotFound(UUID slotId) {
        return slotRepository.existsById(slotId)
                ? new ApplicationException(ErrorCode.CROSS_BUSINESS_ACCESS_FORBIDDEN)
                : new ApplicationException(ErrorCode.SLOT_NOT_FOUND);
    }

    static void requireAppointmentSupport(BusinessService service) {
        if (!service.supportsAppointment()) {
            throw new ApplicationException(ErrorCode.APPOINTMENT_NOT_SUPPORTED);
        }
    }
}
