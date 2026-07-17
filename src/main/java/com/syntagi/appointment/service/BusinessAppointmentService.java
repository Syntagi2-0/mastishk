package com.syntagi.appointment.service;

import com.syntagi.appointment.dto.response.AppointmentResponse;
import com.syntagi.appointment.entity.Appointment;
import com.syntagi.appointment.enums.AppointmentStatus;
import com.syntagi.appointment.repository.AppointmentRepository;
import com.syntagi.auth.service.AuthenticatedBusinessContext;
import com.syntagi.auth.service.AuthenticatedBusinessContextService;
import com.syntagi.common.exception.ApplicationException;
import com.syntagi.common.exception.ErrorCode;
import com.syntagi.queue.service.QueueTimeService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessAppointmentService {

    private final AuthenticatedBusinessContextService contextService;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentCancellationService cancellationService;
    private final AppointmentResponseMapper mapper;
    private final QueueTimeService timeService;

    public BusinessAppointmentService(
            AuthenticatedBusinessContextService contextService,
            AppointmentRepository appointmentRepository,
            AppointmentCancellationService cancellationService,
            AppointmentResponseMapper mapper,
            QueueTimeService timeService) {
        this.contextService = contextService;
        this.appointmentRepository = appointmentRepository;
        this.cancellationService = cancellationService;
        this.mapper = mapper;
        this.timeService = timeService;
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> today(UUID serviceId, AppointmentStatus status) {
        AuthenticatedBusinessContext context = contextService.current();
        LocalDate today = timeService.businessDate(context.business());
        List<Appointment> appointments = appointmentRepository.findAll(filters(
                        context.business().getId(), serviceId, today, today, status, null, null))
                .stream().sorted(java.util.Comparator.comparing(Appointment::getScheduledStartTime))
                .toList();
        return mapper.businessAppointments(appointments);
    }

    @Transactional(readOnly = true)
    public Page<AppointmentResponse> list(
            UUID serviceId,
            LocalDate dateFrom,
            LocalDate dateTo,
            AppointmentStatus status,
            String bookingReference,
            String search,
            Pageable pageable) {
        AuthenticatedBusinessContext context = contextService.current();
        if (dateFrom != null && dateTo != null && dateTo.isBefore(dateFrom)) {
            throw new ApplicationException(ErrorCode.INVALID_TIME_RANGE,
                    "dateTo must be on or after dateFrom");
        }
        Page<Appointment> appointments = appointmentRepository.findAll(filters(
                        context.business().getId(), serviceId, dateFrom, dateTo,
                        status, bookingReference, search), safePage(pageable));
        return new org.springframework.data.domain.PageImpl<>(
                mapper.businessAppointments(appointments.getContent()),
                appointments.getPageable(), appointments.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(UUID appointmentId) {
        AuthenticatedBusinessContext context = contextService.current();
        Appointment appointment = appointmentRepository
                .findDetailedByIdAndBusinessId(appointmentId, context.business().getId())
                .orElseThrow(() -> appointmentRepository.existsById(appointmentId)
                        ? new ApplicationException(ErrorCode.CROSS_BUSINESS_ACCESS_FORBIDDEN)
                        : new ApplicationException(ErrorCode.APPOINTMENT_NOT_FOUND));
        return mapper.businessAppointment(appointment);
    }

    @Transactional
    public AppointmentResponse cancel(UUID appointmentId, String reason) {
        AuthenticatedBusinessContext context = contextService.current();
        return mapper.businessAppointment(cancellationService.cancelByBusiness(
                appointmentId, context.business().getId(), reason));
    }

    private static Specification<Appointment> filters(
            UUID businessId,
            UUID serviceId,
            LocalDate dateFrom,
            LocalDate dateTo,
            AppointmentStatus status,
            String bookingReference,
            String search) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            predicates.add(cb.equal(root.get("business").get("id"), businessId));
            if (serviceId != null) {
                predicates.add(cb.equal(root.get("businessService").get("id"), serviceId));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("appointmentDate"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("appointmentDate"), dateTo));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (bookingReference != null && !bookingReference.isBlank()) {
                predicates.add(cb.equal(root.get("bookingReference"), bookingReference.trim()));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("bookingReference")), pattern),
                        cb.like(cb.lower(root.get("customer").get("fullName")), pattern),
                        cb.like(cb.lower(root.get("customer").get("mobile")), pattern),
                        cb.like(cb.lower(root.get("businessService").get("name")), pattern)));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private static Pageable safePage(Pageable pageable) {
        return org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(),
                org.springframework.data.domain.Sort.by("appointmentDate", "scheduledStartTime"));
    }
}
