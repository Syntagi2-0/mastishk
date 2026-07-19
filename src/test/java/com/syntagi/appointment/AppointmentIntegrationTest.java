package com.syntagi.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syntagi.appointment.dto.request.BookAppointmentRequest;
import com.syntagi.appointment.dto.request.CancelAppointmentRequest;
import com.syntagi.appointment.dto.request.BusinessCancelAppointmentRequest;
import com.syntagi.appointment.dto.request.GenerateSlotsRequest;
import com.syntagi.appointment.entity.Appointment;
import com.syntagi.appointment.entity.AppointmentSlot;
import com.syntagi.appointment.enums.AppointmentStatus;
import com.syntagi.appointment.repository.AppointmentRepository;
import com.syntagi.appointment.repository.AppointmentSlotRepository;
import com.syntagi.appointment.service.AppointmentQueueTokenService;
import com.syntagi.auth.dto.request.RegisterOwnerRequest;
import com.syntagi.customer.repository.CustomerRepository;
import com.syntagi.queue.dto.request.WalkInRequest;
import com.syntagi.queue.entity.QueueSession;
import com.syntagi.queue.entity.QueueToken;
import com.syntagi.queue.enums.QueueTokenStatus;
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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AppointmentIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

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
    @Autowired CustomerRepository customerRepository;
    @Autowired QueueSessionRepository sessionRepository;
    @Autowired QueueTokenRepository tokenRepository;
    @Autowired QueueSessionProvisioningService provisioningService;
    @Autowired AppointmentQueueTokenService appointmentQueueTokenService;

    @Test
    void generatesSlotsFromActiveSchedulesForAppointmentAndBothAndRejectsDuplicatesAndWalkIn()
            throws Exception {
        Owner owner = register("slot-generation");
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);
        BusinessService appointment = service(owner, ServiceMode.APPOINTMENT, 30);
        schedule(appointment, date, LocalTime.of(9, 0), LocalTime.of(11, 0), true, true);

        mockMvc.perform(post("/api/appointment-slots/generate")
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new GenerateSlotsRequest(appointment.getId(), date, date))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.generatedCount").value(4));
        assertThat(slotRepository.findAvailableByServiceAndDate(appointment.getId(), date))
                .extracting(AppointmentSlot::getStartTime)
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 30),
                        LocalTime.of(10, 0), LocalTime.of(10, 30));

        mockMvc.perform(post("/api/appointment-slots/generate")
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new GenerateSlotsRequest(appointment.getId(), date, date))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SLOT_GENERATION"));

        BusinessService both = service(owner, ServiceMode.BOTH, 60);
        schedule(both, date, LocalTime.of(12, 0), LocalTime.of(14, 0), true, true);
        generate(owner, both, date);
        assertThat(slotRepository.findAvailableByServiceAndDate(both.getId(), date)).hasSize(2);

        BusinessService walkIn = service(owner, ServiceMode.WALK_IN, null);
        mockMvc.perform(post("/api/appointment-slots/generate")
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new GenerateSlotsRequest(walkIn.getId(), date, date))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_SUPPORTED"));
    }

    @Test
    void bookingReusesCustomerIncrementsCapacityAndDoesNotCreateToken() throws Exception {
        Owner owner = register("booking");
        BusinessService service = service(owner, ServiceMode.APPOINTMENT, 30);
        AppointmentSlot first = slot(service, LocalDate.now().plusDays(2), LocalTime.of(10, 0), 2);
        AppointmentSlot second = slot(service, LocalDate.now().plusDays(2), LocalTime.of(11, 0), 2);

        JsonNode booked = book(owner, service, first, "Patient", "+919811110001");
        book(owner, service, second, "Changed Name", "+919811110001");

        assertThat(booked.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(slotRepository.findById(first.getId()).orElseThrow().getBookedCount()).isEqualTo(1);
        assertThat(customerRepository.findByBusinessIdAndMobile(
                service.getBusiness().getId(), "+919811110001")).isPresent();
        Appointment appointment = appointmentRepository
                .findByBookingReference(booked.path("bookingReference").asText()).orElseThrow();
        assertThat(tokenRepository.findByAppointmentId(appointment.getId())).isEmpty();
    }

    @Test
    void lookupRequiresMatchingMobileAndCancellationReleasesCapacity() throws Exception {
        Owner owner = register("lookup-cancel");
        BusinessService service = service(owner, ServiceMode.APPOINTMENT, 30);
        AppointmentSlot slot = slot(service, LocalDate.now().plusDays(3), LocalTime.of(10, 0), 1);
        JsonNode booked = book(owner, service, slot, "Lookup Patient", "+919822220001");
        String reference = booked.path("bookingReference").asText();

        mockMvc.perform(get("/api/public/appointments/{reference}", reference)
                        .param("mobile", "+919822220001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerName").value("Lookup Patient"))
                .andExpect(jsonPath("$.data.appointmentId").doesNotExist());
        mockMvc.perform(get("/api/public/appointments/{reference}", reference)
                        .param("mobile", "+919800000000"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MOBILE_MISMATCH"));

        mockMvc.perform(post("/api/public/appointments/{reference}/cancel", reference)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CancelAppointmentRequest("+919822220001", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getBookedCount()).isZero();
    }

    @Test
    void concurrentBookingCannotOverbook() throws Exception {
        Owner owner = register("concurrent");
        BusinessService service = service(owner, ServiceMode.APPOINTMENT, 30);
        AppointmentSlot slot = slot(service, LocalDate.now().plusDays(4), LocalTime.of(10, 0), 1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> bookingStatus(
                    owner, service, slot, "One", "+919833330001"));
            Future<Integer> second = executor.submit(() -> bookingStatus(
                    owner, service, slot, "Two", "+919833330002"));
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getBookedCount()).isEqualTo(1);
    }

    @Test
    void sessionCreationGeneratesAppointmentTokensIdempotently() throws Exception {
        Owner owner = register("token-generation");
        BusinessService service = service(owner, ServiceMode.APPOINTMENT, 30);
        LocalDate today = businessToday(service);
        schedule(service, today, LocalTime.MIN, LocalTime.of(23, 59), true, false);
        AppointmentSlot slot = slot(service, today, LocalTime.MIN, 1);
        JsonNode booked = book(owner, service, slot, "Token Patient", "+919844440001");
        Appointment appointment = appointmentRepository.findByBookingReference(
                booked.path("bookingReference").asText()).orElseThrow();
        assertThat(tokenRepository.findByAppointmentId(appointment.getId())).isEmpty();

        assertThat(provisioningService.createDueSessions(Instant.now())).isEqualTo(1);
        QueueSession session = session(service, today);
        QueueToken token = tokenRepository.findByAppointmentId(appointment.getId()).orElseThrow();
        assertThat(token.getTokenDisplay()).isEqualTo("A001");
        assertThat(token.getStatus()).isEqualTo(QueueTokenStatus.WAITING);
        assertThat(appointmentQueueTokenService.generateFor(session)).isZero();
        assertThat(tokenRepository.findLiveByQueueSessionId(session.getId())).hasSize(1);
    }

    @Test
    void businessCancellationClearsAnActivelyCalledAppointmentToken() throws Exception {
        Owner owner = register("called-token-cancel");
        BusinessService service = service(owner, ServiceMode.APPOINTMENT, 30);
        LocalDate today = businessToday(service);
        schedule(service, today, LocalTime.MIN, LocalTime.of(23, 59), true, false);
        JsonNode booked = book(owner, service, slot(service, today, LocalTime.MIN, 1),
                "Called Patient", "+919844440002");
        Appointment appointment = appointmentRepository.findByBookingReference(
                booked.path("bookingReference").asText()).orElseThrow();
        provisioningService.createDueSessions(Instant.now());

        mockMvc.perform(post("/api/queue/next")
                        .param("serviceId", service.getId().toString())
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentToken").value("A001"));
        mockMvc.perform(post("/api/appointments/{id}/cancel", appointment.getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new BusinessCancelAppointmentRequest("Unable to serve"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        assertThat(session(service, today).getCurrentToken()).isNull();
        assertThat(tokenRepository.findByAppointmentId(appointment.getId()).orElseThrow().getStatus())
                .isEqualTo(QueueTokenStatus.CANCELLED);
        mockMvc.perform(post("/api/queue/next")
                        .param("serviceId", service.getId().toString())
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentToken").isEmpty());
    }

    @Test
    void futureAppointmentWaitsWhileWalkInMoves() throws Exception {
        Owner owner = register("future-order");
        BusinessService service = service(owner, ServiceMode.BOTH, 30);
        LocalDate today = businessToday(service);
        schedule(service, today, LocalTime.MIN, LocalTime.of(23, 59), true, true);
        LocalTime future = LocalTime.now(ZoneId.of(service.getBusiness().getTimezone())).plusHours(1);
        AppointmentSlot slot = slot(service, today, future, 1);
        JsonNode booked = book(owner, service, slot, "Future Patient", "+919855550001");
        provisioningService.createDueSessions(Instant.now());

        mockMvc.perform(post("/api/public/businesses/{code}/walk-in", owner.publicCode())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new WalkInRequest(
                                service.getId(), "Walk In", "+919855550002"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/queue/next")
                        .param("serviceId", service.getId().toString())
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentToken").value("W001"));
        Appointment appointment = appointmentRepository.findByBookingReference(
                booked.path("bookingReference").asText()).orElseThrow();
        assertThat(tokenRepository.findByAppointmentId(appointment.getId()).orElseThrow().getStatus())
                .isEqualTo(QueueTokenStatus.WAITING);
    }

    @Test
    void appointmentTokensUseScheduledOrderAndNextCompletesLinkedAppointment() throws Exception {
        Owner owner = register("scheduled-order");
        BusinessService service = service(owner, ServiceMode.APPOINTMENT, 1);
        LocalDate today = businessToday(service);
        schedule(service, today, LocalTime.MIN, LocalTime.of(23, 59), true, false);
        JsonNode later = book(owner, service, slot(service, today, LocalTime.of(0, 2), 1),
                "Later", "+919866660002");
        JsonNode earlier = book(owner, service, slot(service, today, LocalTime.of(0, 1), 1),
                "Earlier", "+919866660001");
        provisioningService.createDueSessions(Instant.now());

        mockMvc.perform(post("/api/queue/next")
                        .param("serviceId", service.getId().toString())
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentToken").value("A001"));
        mockMvc.perform(post("/api/queue/next")
                        .param("serviceId", service.getId().toString())
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentToken").value("A002"));
        assertThat(appointmentRepository.findByBookingReference(
                earlier.path("bookingReference").asText()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(appointmentRepository.findByBookingReference(
                later.path("bookingReference").asText()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void businessAppointmentAccessIsTenantScoped() throws Exception {
        Owner first = register("cross-first");
        Owner second = register("cross-second");
        BusinessService service = service(second, ServiceMode.APPOINTMENT, 30);
        AppointmentSlot slot = slot(service, LocalDate.now().plusDays(5), LocalTime.of(10, 0), 1);
        JsonNode booked = book(second, service, slot, "Private", "+919877770001");
        UUID id = appointmentRepository.findByBookingReference(
                booked.path("bookingReference").asText()).orElseThrow().getId();

        mockMvc.perform(get("/api/appointments/{id}", id)
                        .header("Authorization", bearer(first.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_BUSINESS_ACCESS_FORBIDDEN"));
    }

    @Test
    void dashboardGroupsTodaysAppointmentsByServiceAndTime() throws Exception {
        Owner owner = register("dashboard-appointments");
        BusinessService service = service(owner, ServiceMode.APPOINTMENT, 30);
        LocalDate today = businessToday(service);
        book(owner, service, slot(service, today, LocalTime.of(11, 0), 1),
                "Later Patient", "+919888880002");
        book(owner, service, slot(service, today, LocalTime.of(10, 0), 1),
                "Earlier Patient", "+919888880001");

        mockMvc.perform(get("/api/dashboard/today-appointments")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.services.length()").value(1))
                .andExpect(jsonPath("$.data.services[0].serviceId")
                        .value(service.getId().toString()))
                .andExpect(jsonPath("$.data.services[0].appointments[0].customerName")
                        .value("Earlier Patient"))
                .andExpect(jsonPath("$.data.services[0].appointments[1].customerName")
                        .value("Later Patient"));

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appointments.totalToday").value(2))
                .andExpect(jsonPath("$.data.appointments.confirmed").value(2));
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

    private ServiceSchedule schedule(BusinessService service, LocalDate date,
            LocalTime start, LocalTime end, boolean appointment, boolean walkIn) {
        ServiceSchedule schedule = new ServiceSchedule(service, date.getDayOfWeek(), start, end);
        schedule.configureBookingModes(appointment, walkIn);
        return scheduleRepository.save(schedule);
    }

    private AppointmentSlot slot(BusinessService service, LocalDate date, LocalTime start, int capacity) {
        return slotRepository.save(new AppointmentSlot(service.getBusiness(), service, date,
                start, start.plusMinutes(Math.max(1, service.getAppointmentSlotDurationMinutes())), capacity));
    }

    private void generate(Owner owner, BusinessService service, LocalDate date) throws Exception {
        mockMvc.perform(post("/api/appointment-slots/generate")
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new GenerateSlotsRequest(service.getId(), date, date))))
                .andExpect(status().isCreated());
    }

    private JsonNode book(Owner owner, BusinessService service, AppointmentSlot slot,
            String name, String mobile) throws Exception {
        return data(mockMvc.perform(post("/api/public/businesses/{code}/appointments", owner.publicCode())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new BookAppointmentRequest(
                                service.getId(), slot.getId(), name, mobile, null, null))))
                .andExpect(status().isCreated()).andReturn());
    }

    private int bookingStatus(Owner owner, BusinessService service, AppointmentSlot slot,
            String name, String mobile) throws Exception {
        return mockMvc.perform(post("/api/public/businesses/{code}/appointments", owner.publicCode())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new BookAppointmentRequest(
                                service.getId(), slot.getId(), name, mobile, null, null))))
                .andReturn().getResponse().getStatus();
    }

    private QueueSession session(BusinessService service, LocalDate date) {
        return sessionRepository.findByBusinessServiceIdAndBusinessDate(service.getId(), date)
                .orElseThrow();
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

    private record Owner(String token, String publicCode) {
    }
}
