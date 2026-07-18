package com.syntagi.servicecatalog.mapper;

import com.syntagi.servicecatalog.dto.response.PublicServiceResponse;
import com.syntagi.servicecatalog.dto.response.ScheduleResponse;
import com.syntagi.servicecatalog.dto.response.ServiceResponse;
import com.syntagi.servicecatalog.entity.BusinessService;
import com.syntagi.servicecatalog.entity.ServiceSchedule;
import com.syntagi.queue.enums.QueueSessionStatus;
import org.springframework.stereotype.Component;

@Component
public class ServiceCatalogMapper {

    public ServiceResponse toServiceResponse(BusinessService service) {
        if (service == null) {
            return null;
        }
        return new ServiceResponse(
                service.getId(),
                service.getName(),
                service.getDescription(),
                service.getServiceCode(),
                service.getServiceMode(),
                service.getExpectedDurationMinutes(),
                service.getAppointmentSlotDurationMinutes(),
                service.isActive(),
                service.getDisplayOrder());
    }

    public PublicServiceResponse toPublicServiceResponse(
            BusinessService service, QueueSessionStatus queueStatus) {
        if (service == null) {
            return null;
        }
        return new PublicServiceResponse(
                service.getId(),
                service.getName(),
                service.getDescription(),
                service.getServiceMode(),
                service.getExpectedDurationMinutes(),
                service.supportsWalkIn(),
                service.supportsAppointment(),
                queueStatus);
    }

    public ScheduleResponse toScheduleResponse(ServiceSchedule schedule) {
        if (schedule == null) {
            return null;
        }
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getDayOfWeek().getValue(),
                schedule.getOperatingStartTime(),
                schedule.getOperatingEndTime(),
                schedule.getQueueOpenBeforeMinutes(),
                schedule.isAppointmentBookingEnabled(),
                schedule.isWalkInEnabled(),
                schedule.isActive());
    }
}
