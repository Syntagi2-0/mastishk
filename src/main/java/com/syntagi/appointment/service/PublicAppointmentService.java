package com.syntagi.appointment.service;

import com.syntagi.appointment.dto.request.BookAppointmentRequest;
import com.syntagi.appointment.dto.request.CancelAppointmentRequest;
import com.syntagi.appointment.dto.response.PublicAppointmentResponse;
import com.syntagi.appointment.dto.response.PublicSlotResponse;
import com.syntagi.appointment.entity.Appointment;
import com.syntagi.appointment.entity.AppointmentSlot;
import com.syntagi.appointment.enums.AppointmentSlotStatus;
import com.syntagi.appointment.repository.AppointmentRepository;
import com.syntagi.appointment.repository.AppointmentSlotRepository;
import com.syntagi.business.entity.Business;
import com.syntagi.business.enums.BusinessStatus;
import com.syntagi.business.repository.BusinessRepository;
import com.syntagi.common.exception.ApplicationException;
import com.syntagi.common.exception.ErrorCode;
import com.syntagi.customer.entity.Customer;
import com.syntagi.customer.repository.CustomerRepository;
import com.syntagi.servicecatalog.entity.BusinessService;
import com.syntagi.servicecatalog.repository.BusinessServiceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.syntagi.notification.service.NotificationService;

@Service
public class PublicAppointmentService {

    private final BusinessRepository businessRepository;
    private final BusinessServiceRepository serviceRepository;
    private final AppointmentSlotRepository slotRepository;
    private final CustomerRepository customerRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentCancellationService cancellationService;
    private final AppointmentResponseMapper mapper;
    private final NotificationService notificationService;

    public PublicAppointmentService(
            BusinessRepository businessRepository,
            BusinessServiceRepository serviceRepository,
            AppointmentSlotRepository slotRepository,
            CustomerRepository customerRepository,
            AppointmentRepository appointmentRepository,
            AppointmentCancellationService cancellationService,
            AppointmentResponseMapper mapper,
            NotificationService notificationService) {
        this.businessRepository = businessRepository;
        this.serviceRepository = serviceRepository;
        this.slotRepository = slotRepository;
        this.customerRepository = customerRepository;
        this.appointmentRepository = appointmentRepository;
        this.cancellationService = cancellationService;
        this.mapper = mapper;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<PublicSlotResponse> availableSlots(
            String publicQueueCode, UUID serviceId, LocalDate date) {
        Business business = activeBusiness(publicQueueCode);
        BusinessService service = activeService(serviceId, business.getId());
        AppointmentSlotService.requireAppointmentSupport(service);
        return slotRepository.findAvailableByServiceAndDate(serviceId, date).stream()
                .map(slot -> new PublicSlotResponse(
                        slot.getId().toString(), slot.getStartTime(), slot.getEndTime(),
                        slot.getCapacity() - slot.getBookedCount()))
                .toList();
    }

    @Transactional
    public PublicAppointmentResponse book(String publicQueueCode, BookAppointmentRequest request) {
        Business business = activeBusiness(publicQueueCode);
        BusinessService service = activeService(request.serviceId(), business.getId());
        AppointmentSlotService.requireAppointmentSupport(service);
        AppointmentSlot slot = slotRepository.findByIdAndBusinessServiceIdForUpdate(
                        request.slotId(), service.getId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.SLOT_NOT_FOUND));
        if (!slot.getBusiness().getId().equals(business.getId())) {
            throw new ApplicationException(ErrorCode.SLOT_NOT_FOUND);
        }
        if (slot.getStatus() != AppointmentSlotStatus.AVAILABLE) {
            throw new ApplicationException(ErrorCode.SLOT_UNAVAILABLE);
        }
        if (slot.getBookedCount() >= slot.getCapacity()) {
            throw new ApplicationException(ErrorCode.SLOT_FULL);
        }
        String mobile = request.mobile().trim();
        Customer customer = customerRepository.findByBusinessIdAndMobile(business.getId(), mobile)
                .orElseGet(() -> customerRepository.save(new Customer(
                        business, request.fullName(), mobile, request.email())));
        slot.reserveOne();
        Appointment appointment = appointmentRepository.save(new Appointment(
                business, service, customer, slot, uniqueReference(), slot.getSlotDate(),
                slot.getStartTime(), slot.getEndTime(), request.customerNotes()));
        notificationService.appointmentBooked(appointment);
        return mapper.publicAppointment(appointment);
    }

    @Transactional(readOnly = true)
    public PublicAppointmentResponse lookup(String bookingReference, String mobile) {
        Appointment appointment = appointmentRepository
                .findDetailedByBookingReference(bookingReference.trim())
                .orElseThrow(() -> new ApplicationException(ErrorCode.APPOINTMENT_NOT_FOUND));
        if (!appointment.getCustomer().getMobile().equals(mobile.trim())) {
            throw new ApplicationException(ErrorCode.MOBILE_MISMATCH);
        }
        return mapper.publicAppointment(appointment);
    }

    @Transactional
    public PublicAppointmentResponse cancel(
            String bookingReference, CancelAppointmentRequest request) {
        return mapper.publicAppointment(cancellationService.cancelByReference(
                bookingReference, request.mobile(), request.reason()));
    }

    private Business activeBusiness(String code) {
        return businessRepository.findByPublicQueueCode(code)
                .filter(business -> business.getStatus() == BusinessStatus.ACTIVE)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private BusinessService activeService(UUID id, UUID businessId) {
        return serviceRepository.findByIdAndBusinessId(id, businessId)
                .filter(BusinessService::isActive)
                .orElseThrow(() -> new ApplicationException(ErrorCode.SERVICE_NOT_FOUND));
    }

    private String uniqueReference() {
        String reference;
        do {
            reference = "APT-" + UUID.randomUUID().toString()
                    .replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        } while (appointmentRepository.existsByBookingReference(reference));
        return reference;
    }
}
