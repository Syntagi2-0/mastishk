package com.syntagi.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syntagi.appointment.dto.request.BookAppointmentRequest;
import com.syntagi.appointment.dto.request.CancelAppointmentRequest;
import com.syntagi.appointment.entity.Appointment;
import com.syntagi.appointment.entity.AppointmentSlot;
import com.syntagi.appointment.repository.AppointmentRepository;
import com.syntagi.appointment.repository.AppointmentSlotRepository;
import com.syntagi.auth.dto.request.RegisterOwnerRequest;
import com.syntagi.notification.entity.Notification;
import com.syntagi.notification.enums.*;
import com.syntagi.notification.repository.NotificationRepository;
import com.syntagi.notification.service.NotificationService;
import com.syntagi.queue.dto.request.WalkInRequest;
import com.syntagi.queue.entity.QueueSession;
import com.syntagi.queue.entity.QueueToken;
import com.syntagi.queue.repository.QueueSessionRepository;
import com.syntagi.queue.repository.QueueTokenRepository;
import com.syntagi.queue.service.QueueSessionProvisioningService;
import com.syntagi.servicecatalog.dto.request.ServiceUpsertRequest;
import com.syntagi.servicecatalog.entity.BusinessService;
import com.syntagi.servicecatalog.entity.ServiceSchedule;
import com.syntagi.servicecatalog.enums.ServiceMode;
import com.syntagi.servicecatalog.repository.BusinessServiceRepository;
import com.syntagi.servicecatalog.repository.ServiceScheduleRepository;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class NotificationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("syntagi.queue.scheduler.cron", () -> "0 0 0 1 1 *");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BusinessServiceRepository serviceRepository;
    @Autowired ServiceScheduleRepository scheduleRepository;
    @Autowired AppointmentSlotRepository slotRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired QueueSessionRepository sessionRepository;
    @Autowired QueueTokenRepository tokenRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired NotificationService notificationService;
    @Autowired QueueSessionProvisioningService provisioningService;

    @Test
    void appointmentBookingAndCancellationCreateCustomerScopedNotifications() throws Exception {
        Owner owner = register("appointment-events");
        BusinessService service = service(owner, ServiceMode.APPOINTMENT, 30);
        AppointmentSlot slot = slot(service, businessToday(service).plusDays(1), LocalTime.of(10, 0));
        JsonNode booked = book(owner, service, slot, "Patient One", "+919910000001");
        String reference = booked.path("bookingReference").asText();

        mockMvc.perform(get("/api/public/notifications")
                        .param("bookingReference", reference)
                        .param("mobile", "+919910000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].notificationType")
                        .value("APPOINTMENT_BOOKED"))
                .andExpect(jsonPath("$.data.content[0].notificationId").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].failureReason").doesNotExist());

        mockMvc.perform(get("/api/public/notifications")
                        .param("bookingReference", reference)
                        .param("mobile", "+919910009999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.totalElements").value(0));
        mockMvc.perform(get("/api/public/notifications").param("mobile", "+919910000001"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/public/appointments/{reference}/cancel", reference)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CancelAppointmentRequest("+919910000001", "Changed plans"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/notifications")
                        .param("bookingReference", reference)
                        .param("mobile", "+919910000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].notificationType")
                        .value("APPOINTMENT_CANCELLED"));
    }

    @Test
    void appointmentTokenGenerationCreatesOneDeduplicatedNotification() throws Exception {
        Owner owner = register("token-generated");
        BusinessService service = service(owner, ServiceMode.APPOINTMENT, 30);
        LocalDate today = businessToday(service);
        schedule(service, today, true, false);
        JsonNode booked = book(owner, service, slot(service, today, LocalTime.MIN),
                "Token Patient", "+919920000001");
        Appointment appointment = appointmentRepository.findByBookingReference(
                booked.path("bookingReference").asText()).orElseThrow();

        provisioningService.createDueSessions(Instant.now());
        QueueToken token = tokenRepository.findByAppointmentId(appointment.getId()).orElseThrow();
        List<Notification> tokenNotifications = notificationRepository
                .findByQueueTokenIdOrderByCreatedAtAsc(token.getId()).stream()
                .filter(n -> n.getNotificationType() == NotificationType.APPOINTMENT_TOKEN_GENERATED)
                .toList();
        assertThat(tokenNotifications).hasSize(1);
        assertThat(tokenNotifications.getFirst().getMessage())
                .contains(appointment.getBookingReference(), token.getTokenDisplay());

        notificationService.appointmentTokenGenerated(token);
        assertThat(notificationRepository.findByQueueTokenIdOrderByCreatedAtAsc(token.getId()).stream()
                .filter(n -> n.getNotificationType() == NotificationType.APPOINTMENT_TOKEN_GENERATED))
                .hasSize(1);
    }

    @Test
    void callSkipAndRecallProduceExpectedNotificationsWithoutRecallDuplicate() throws Exception {
        Owner owner = register("queue-events");
        BusinessService service = service(owner, ServiceMode.WALK_IN, null);
        LocalDate today = businessToday(service);
        schedule(service, today, false, true);
        provisioningService.createDueSessions(Instant.now());
        walkIn(owner, service, "First", "+919930000001");
        walkIn(owner, service, "Second", "+919930000002");

        next(owner, service);
        QueueSession session = session(service, today);
        QueueToken first = tokenRepository.findByQueueSessionIdAndTokenDisplay(
                session.getId(), "W001").orElseThrow();
        assertTypeCount(first, NotificationType.QUEUE_TOKEN_CALLED, 1);

        mockMvc.perform(post("/api/queue/current/recall")
                        .param("serviceId", service.getId().toString())
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk());
        assertTypeCount(first, NotificationType.QUEUE_TOKEN_CALLED, 1);
        notificationService.tokenCalled(first);
        assertTypeCount(first, NotificationType.QUEUE_TOKEN_CALLED, 1);

        mockMvc.perform(post("/api/queue/current/skip")
                        .param("serviceId", service.getId().toString())
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentToken").value("W002"));
        assertTypeCount(first, NotificationType.QUEUE_TOKEN_SKIPPED, 1);
    }

    @Test
    void publicTokenNotificationLookupDoesNotCrossMatchBusinesses() throws Exception {
        String mobile = "+919930000099";
        Owner firstOwner = register("public-token-first");
        BusinessService firstService = service(firstOwner, ServiceMode.WALK_IN, null);
        LocalDate firstToday = businessToday(firstService);
        schedule(firstService, firstToday, false, true);
        provisioningService.createDueSessions(Instant.now());
        walkIn(firstOwner, firstService, "Shared Mobile", mobile);
        next(firstOwner, firstService);

        Owner secondOwner = register("public-token-second");
        BusinessService secondService = service(secondOwner, ServiceMode.WALK_IN, null);
        LocalDate secondToday = businessToday(secondService);
        schedule(secondService, secondToday, false, true);
        provisioningService.createDueSessions(Instant.now());
        walkIn(secondOwner, secondService, "Shared Mobile", mobile);
        next(secondOwner, secondService);

        mockMvc.perform(get("/api/public/notifications")
                        .param("tokenDisplay", "W001")
                        .param("mobile", mobile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.totalElements").value(1));
    }

    @Test
    void cancellingAppointmentTokenCreatesQueueCancellationNotification() throws Exception {
        Owner owner = register("token-cancelled");
        BusinessService service = service(owner, ServiceMode.APPOINTMENT, 30);
        LocalDate today = businessToday(service);
        schedule(service, today, true, false);
        JsonNode booked = book(owner, service, slot(service, today, LocalTime.of(23, 0)),
                "Cancel Token", "+919940000001");
        Appointment appointment = appointmentRepository.findByBookingReference(
                booked.path("bookingReference").asText()).orElseThrow();
        provisioningService.createDueSessions(Instant.now());
        QueueToken token = tokenRepository.findByAppointmentId(appointment.getId()).orElseThrow();

        mockMvc.perform(post("/api/public/appointments/{reference}/cancel",
                        appointment.getBookingReference())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CancelAppointmentRequest("+919940000001", null))))
                .andExpect(status().isOk());
        assertTypeCount(token, NotificationType.QUEUE_TOKEN_CANCELLED, 1);
    }

    @Test
    void businessListingPendingCountFiltersPaginationAndMarkSentAreTenantSafe() throws Exception {
        Owner first = register("business-first");
        Owner second = register("business-second");
        BusinessService firstService = service(first, ServiceMode.APPOINTMENT, 30);
        BusinessService secondService = service(second, ServiceMode.APPOINTMENT, 30);
        JsonNode firstBooking = book(first, firstService,
                slot(firstService, businessToday(firstService).plusDays(1), LocalTime.of(9, 0)),
                "First Customer", "+919950000001");
        book(second, secondService,
                slot(secondService, businessToday(secondService).plusDays(1), LocalTime.of(9, 0)),
                "Second Customer", "+919950000002");

        MvcResult list = mockMvc.perform(get("/api/notifications")
                        .param("status", "PENDING")
                        .param("notificationType", "APPOINTMENT_BOOKED")
                        .param("customerMobile", "+919950000001")
                        .param("bookingReference", firstBooking.path("bookingReference").asText())
                        .param("page", "0").param("size", "1")
                        .header("Authorization", bearer(first.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].customerMobile").value("+919950000001"))
                .andReturn();
        UUID notificationId = UUID.fromString(data(list).path("content").get(0)
                .path("notificationId").asText());
        mockMvc.perform(get("/api/notifications/pending-count")
                        .header("Authorization", bearer(first.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingCount").value(1));
        mockMvc.perform(post("/api/notifications/{id}/mark-sent", notificationId)
                        .header("Authorization", bearer(second.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/notifications/{id}/mark-sent", notificationId)
                        .header("Authorization", bearer(first.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.data.sentAt").isNotEmpty());
        mockMvc.perform(get("/api/notifications/pending-count")
                        .header("Authorization", bearer(first.token())))
                .andExpect(jsonPath("$.data.pendingCount").value(0));
    }

    @Test
    void internalDeliveryUpdatesStoreSafeFailureAndSkippedState() throws Exception {
        Owner owner = register("delivery-state");
        BusinessService service = service(owner, ServiceMode.APPOINTMENT, 30);
        JsonNode first = book(owner, service,
                slot(service, businessToday(service).plusDays(1), LocalTime.of(8, 0)),
                "Failure", "+919960000001");
        JsonNode second = book(owner, service,
                slot(service, businessToday(service).plusDays(1), LocalTime.of(9, 0)),
                "Skipped", "+919960000002");
        Notification failed = notificationRepository.findByAppointmentIdOrderByCreatedAtAsc(
                appointment(first).getId()).getFirst();
        Notification skipped = notificationRepository.findByAppointmentIdOrderByCreatedAtAsc(
                appointment(second).getId()).getFirst();

        notificationService.markFailed(failed.getId(), "Browser\nerror\t" + "x".repeat(700));
        notificationService.markSkipped(skipped.getId());
        Notification reloadedFailed = notificationRepository.findById(failed.getId()).orElseThrow();
        assertThat(reloadedFailed.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(reloadedFailed.getFailureReason()).doesNotContain("\n", "\t").hasSize(500);
        assertThat(notificationRepository.findById(skipped.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.SKIPPED);
    }

    private Owner register(String key) throws Exception {
        JsonNode data = data(mockMvc.perform(post("/api/auth/register-owner")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RegisterOwnerRequest(
                                "Owner " + key, "Business " + key + UUID.randomUUID(),
                                key + UUID.randomUUID() + "@example.com", "OwnerPassword123",
                                "Asia/Kolkata"))))
                .andExpect(status().isCreated()).andReturn());
        return new Owner(data.path("accessToken").asText(),
                data.path("business").path("publicQueueCode").asText());
    }

    private BusinessService service(Owner owner, ServiceMode mode, Integer duration) throws Exception {
        JsonNode data = data(mockMvc.perform(post("/api/services")
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ServiceUpsertRequest(
                                "Service " + UUID.randomUUID(), null, UUID.randomUUID().toString(),
                                mode, 15, duration, 0))))
                .andExpect(status().isCreated()).andReturn());
        return serviceRepository.findWithBusinessById(UUID.fromString(data.path("id").asText()))
                .orElseThrow();
    }

    private void schedule(BusinessService service, LocalDate date,
            boolean appointment, boolean walkIn) {
        ServiceSchedule schedule = new ServiceSchedule(
                service, date.getDayOfWeek(), LocalTime.MIN, LocalTime.of(23, 59));
        schedule.configureBookingModes(appointment, walkIn);
        scheduleRepository.save(schedule);
    }

    private AppointmentSlot slot(BusinessService service, LocalDate date, LocalTime start) {
        return slotRepository.save(new AppointmentSlot(service.getBusiness(), service, date,
                start, start.plusMinutes(Math.max(1, service.getAppointmentSlotDurationMinutes())), 1));
    }

    private JsonNode book(Owner owner, BusinessService service, AppointmentSlot slot,
            String name, String mobile) throws Exception {
        return data(mockMvc.perform(post("/api/public/businesses/{code}/appointments", owner.code())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new BookAppointmentRequest(
                                service.getId(), slot.getId(), name, mobile, null, null))))
                .andExpect(status().isCreated()).andReturn());
    }

    private void walkIn(Owner owner, BusinessService service, String name, String mobile)
            throws Exception {
        mockMvc.perform(post("/api/public/businesses/{code}/walk-in", owner.code())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new WalkInRequest(service.getId(), name, mobile))))
                .andExpect(status().isCreated());
    }

    private void next(Owner owner, BusinessService service) throws Exception {
        mockMvc.perform(post("/api/queue/next")
                        .param("serviceId", service.getId().toString())
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk());
    }

    private Appointment appointment(JsonNode booking) {
        return appointmentRepository.findByBookingReference(
                booking.path("bookingReference").asText()).orElseThrow();
    }

    private QueueSession session(BusinessService service, LocalDate date) {
        return sessionRepository.findByBusinessServiceIdAndBusinessDate(service.getId(), date)
                .orElseThrow();
    }

    private void assertTypeCount(QueueToken token, NotificationType type, int count) {
        assertThat(notificationRepository.findByQueueTokenIdOrderByCreatedAtAsc(token.getId()).stream()
                .filter(notification -> notification.getNotificationType() == type)).hasSize(count);
    }

    private static LocalDate businessToday(BusinessService service) {
        return LocalDate.now(ZoneId.of(service.getBusiness().getTimezone()));
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private record Owner(String token, String code) {
    }
}
