package com.syntagi.dashboard.service;

import com.syntagi.appointment.dto.response.AppointmentResponse;
import com.syntagi.appointment.entity.Appointment;
import com.syntagi.appointment.enums.AppointmentStatus;
import com.syntagi.appointment.repository.AppointmentRepository;
import com.syntagi.appointment.service.AppointmentResponseMapper;
import com.syntagi.auth.enums.BusinessRole;
import com.syntagi.auth.enums.BusinessUserStatus;
import com.syntagi.auth.service.AuthenticatedBusinessContext;
import com.syntagi.auth.service.AuthenticatedBusinessContextService;
import com.syntagi.dashboard.dto.response.DashboardQueueTokenResponse;
import com.syntagi.dashboard.dto.response.DashboardResponse;
import com.syntagi.dashboard.dto.response.TodayAppointmentsResponse;
import com.syntagi.dashboard.dto.response.TodayQueueResponse;
import com.syntagi.queue.entity.QueueToken;
import com.syntagi.queue.enums.QueueTokenStatus;
import com.syntagi.queue.repository.QueueTokenRepository;
import com.syntagi.queue.service.QueueTimeService;
import com.syntagi.servicecatalog.repository.BusinessServiceRepository;
import com.syntagi.staff.repository.BusinessUserRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final AuthenticatedBusinessContextService contextService;
    private final QueueTimeService timeService;
    private final QueueTokenRepository tokenRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentResponseMapper appointmentMapper;
    private final BusinessServiceRepository serviceRepository;
    private final BusinessUserRepository staffRepository;

    public DashboardService(
            AuthenticatedBusinessContextService contextService,
            QueueTimeService timeService,
            QueueTokenRepository tokenRepository,
            AppointmentRepository appointmentRepository,
            AppointmentResponseMapper appointmentMapper,
            BusinessServiceRepository serviceRepository,
            BusinessUserRepository staffRepository) {
        this.contextService = contextService;
        this.timeService = timeService;
        this.tokenRepository = tokenRepository;
        this.appointmentRepository = appointmentRepository;
        this.appointmentMapper = appointmentMapper;
        this.serviceRepository = serviceRepository;
        this.staffRepository = staffRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        AuthenticatedBusinessContext context = contextService.current();
        LocalDate today = timeService.businessDate(context.business());
        List<QueueToken> tokens = tokenRepository.findDashboardTokens(
                context.business().getId(), today);
        List<Appointment> appointments = appointmentRepository
                .findByBusinessIdAndAppointmentDateOrderByScheduledStartTimeAsc(
                        context.business().getId(), today);

        QueueToken current = tokens.stream()
                .filter(token -> token.getStatus() == QueueTokenStatus.CALLED)
                .max(Comparator.comparing(
                        QueueToken::getCalledAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        Map<AppointmentStatus, Long> appointmentCounts = appointments.stream()
                .collect(Collectors.groupingBy(
                        Appointment::getStatus,
                        () -> new EnumMap<>(AppointmentStatus.class),
                        Collectors.counting()));

        return new DashboardResponse(
                new DashboardResponse.BusinessSummary(
                        context.business().getName(), context.business().getBusinessType()),
                new DashboardResponse.QueueSummary(
                        current == null ? null : current.getTokenDisplay(),
                        current == null ? null : current.getCustomer().getFullName(),
                        count(tokens, QueueTokenStatus.WAITING),
                        count(tokens, QueueTokenStatus.SKIPPED),
                        count(tokens, QueueTokenStatus.COMPLETED),
                        tokens.size()),
                new DashboardResponse.AppointmentSummary(
                        appointments.size(),
                        appointmentCounts.getOrDefault(AppointmentStatus.CONFIRMED, 0L),
                        appointmentCounts.getOrDefault(AppointmentStatus.COMPLETED, 0L),
                        appointmentCounts.getOrDefault(AppointmentStatus.CANCELLED, 0L),
                        appointmentCounts.getOrDefault(AppointmentStatus.NO_SHOW, 0L)),
                serviceRepository.countByBusinessIdAndActiveTrue(context.business().getId()),
                staffRepository.countByBusinessIdAndRoleAndStatus(
                        context.business().getId(), BusinessRole.STAFF, BusinessUserStatus.ACTIVE));
    }

    @Transactional(readOnly = true)
    public TodayQueueResponse todayQueue() {
        AuthenticatedBusinessContext context = contextService.current();
        List<QueueToken> tokens = tokenRepository.findDashboardTokens(
                context.business().getId(), timeService.businessDate(context.business()));
        return new TodayQueueResponse(
                group(tokens, QueueTokenStatus.CALLED),
                group(tokens, QueueTokenStatus.WAITING),
                group(tokens, QueueTokenStatus.SKIPPED),
                group(tokens, QueueTokenStatus.COMPLETED));
    }

    @Transactional(readOnly = true)
    public TodayAppointmentsResponse todayAppointments() {
        AuthenticatedBusinessContext context = contextService.current();
        List<Appointment> appointments = appointmentRepository
                .findByBusinessIdAndAppointmentDateOrderByScheduledStartTimeAsc(
                        context.business().getId(), timeService.businessDate(context.business()));
        Map<java.util.UUID, List<Appointment>> grouped = appointments.stream()
                .collect(Collectors.groupingBy(
                        appointment -> appointment.getBusinessService().getId(),
                        java.util.LinkedHashMap::new,
                        Collectors.toList()));
        return new TodayAppointmentsResponse(grouped.values().stream()
                .map(serviceAppointments -> new TodayAppointmentsResponse.ServiceAppointments(
                        serviceAppointments.getFirst().getBusinessService().getId(),
                        serviceAppointments.getFirst().getBusinessService().getName(),
                        appointmentMapper.businessAppointments(serviceAppointments.stream()
                                .sorted(Comparator.comparing(Appointment::getScheduledStartTime))
                                .toList())))
                .toList());
    }

    private static List<DashboardQueueTokenResponse> group(
            List<QueueToken> tokens, QueueTokenStatus status) {
        return tokens.stream()
                .filter(token -> token.getStatus() == status)
                .sorted(Comparator.comparingLong(QueueToken::getQueueOrder))
                .map(DashboardService::queueTokenResponse)
                .toList();
    }

    private static DashboardQueueTokenResponse queueTokenResponse(QueueToken token) {
        return new DashboardQueueTokenResponse(
                token.getTokenDisplay(), token.getBusinessService().getName(),
                token.getCustomer().getFullName(), token.getSourceType(), token.getStatus(),
                token.getScheduledTime(), token.getJoinedAt(), token.getCalledAt(),
                token.getCompletedAt());
    }

    private static long count(List<QueueToken> tokens, QueueTokenStatus status) {
        return tokens.stream().filter(token -> token.getStatus() == status).count();
    }
}
