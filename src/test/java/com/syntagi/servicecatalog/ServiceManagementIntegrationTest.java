package com.syntagi.servicecatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syntagi.auth.dto.request.LoginRequest;
import com.syntagi.auth.dto.request.RegisterOwnerRequest;
import com.syntagi.queue.enums.QueueStatus;
import com.syntagi.queue.repository.QueueConfigurationRepository;
import com.syntagi.queue.repository.QueueSessionRepository;
import com.syntagi.servicecatalog.dto.request.ScheduleStatusRequest;
import com.syntagi.servicecatalog.dto.request.ScheduleUpsertRequest;
import com.syntagi.servicecatalog.dto.request.ServiceStatusRequest;
import com.syntagi.servicecatalog.dto.request.ServiceUpsertRequest;
import com.syntagi.servicecatalog.enums.ServiceMode;
import com.syntagi.servicecatalog.repository.BusinessServiceRepository;
import com.syntagi.staff.dto.request.CreateStaffRequest;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
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
class ServiceManagementIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    @Autowired private QueueSessionRepository queueSessionRepository;
    @Autowired private QueueConfigurationRepository queueConfigurationRepository;
    @Autowired private BusinessServiceRepository businessServiceRepository;

    @Autowired
    ServiceManagementIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    void ownerCreatesWalkInService() throws Exception {
        OwnerSession owner = registerOwner("walk-in");

        JsonNode service = createService(owner.token(), request(
                "Walk-in Consultation", " walk-in ", ServiceMode.WALK_IN, 2));

        assertThat(service.path("serviceCode").asText()).isEqualTo("WALK-IN");
        assertThat(service.path("serviceMode").asText()).isEqualTo("WALK_IN");
        assertThat(service.path("appointmentSlotDurationMinutes").isNull()).isTrue();
        assertThat(service.path("active").asBoolean()).isTrue();
    }

    @Test
    void ownerCreatesAppointmentService() throws Exception {
        OwnerSession owner = registerOwner("appointment");

        JsonNode service = createService(owner.token(), request(
                "Specialist Appointment", "SPEC-APT", ServiceMode.APPOINTMENT, 1));

        assertThat(service.path("serviceMode").asText()).isEqualTo("APPOINTMENT");
        assertThat(service.path("appointmentSlotDurationMinutes").asInt()).isEqualTo(30);
    }

    @Test
    void appointmentDurationIsRequiredForAppointmentAndBoth() throws Exception {
        OwnerSession owner = registerOwner("duration-required");

        for (ServiceMode mode : new ServiceMode[] {ServiceMode.APPOINTMENT, ServiceMode.BOTH}) {
            ServiceUpsertRequest invalid = new ServiceUpsertRequest(
                    "Missing Duration " + mode,
                    null,
                    "NO-DURATION-" + mode,
                    mode,
                    20,
                    null,
                    0);
            mockMvc.perform(post("/api/services")
                            .header("Authorization", bearer(owner.token()))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("APPOINTMENT_DURATION_REQUIRED"));
        }
    }

    @Test
    void duplicateServiceCodeIsRejected() throws Exception {
        OwnerSession owner = registerOwner("duplicate-code");
        createService(owner.token(), request("First Service", "DUP-CODE", ServiceMode.WALK_IN, 0));

        mockMvc.perform(post("/api/services")
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ServiceUpsertRequest(
                                "Second Service", null, "dup-code", ServiceMode.WALK_IN,
                                15, null, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SERVICE_CODE"));
    }

    @Test
    void duplicateServiceNameIsRejected() throws Exception {
        OwnerSession owner = registerOwner("duplicate-name");
        createService(owner.token(), request("Same Name", "FIRST", ServiceMode.WALK_IN, 0));

        mockMvc.perform(post("/api/services")
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ServiceUpsertRequest(
                                "same name", null, "SECOND", ServiceMode.WALK_IN,
                                15, null, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SERVICE_NAME"));
    }

    @Test
    void staffCanListAndViewServices() throws Exception {
        OwnerSession owner = registerOwner("staff-read");
        JsonNode later = createService(owner.token(), request(
                "Zulu Service", "ZULU", ServiceMode.WALK_IN, 2));
        JsonNode first = createService(owner.token(), request(
                "Alpha Service", "ALPHA", ServiceMode.WALK_IN, 1));
        String staffToken = createAndLoginStaff(owner.token(), uniqueEmail("service-staff"));

        mockMvc.perform(get("/api/services").header("Authorization", bearer(staffToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(first.path("id").asText()))
                .andExpect(jsonPath("$.data[1].id").value(later.path("id").asText()));
        mockMvc.perform(get("/api/services/{id}", first.path("id").asText())
                        .header("Authorization", bearer(staffToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Alpha Service"));
    }

    @Test
    void staffCannotCreateOrUpdateServices() throws Exception {
        OwnerSession owner = registerOwner("staff-write-denied");
        JsonNode service = createService(owner.token(), request(
                "Protected Service", "PROTECTED", ServiceMode.WALK_IN, 0));
        String staffToken = createAndLoginStaff(owner.token(), uniqueEmail("denied-staff"));

        mockMvc.perform(post("/api/services")
                        .header("Authorization", bearer(staffToken))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request(
                                "Forbidden Service", "FORBIDDEN", ServiceMode.WALK_IN, 0))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_ROLE"));
        mockMvc.perform(put("/api/services/{id}", service.path("id").asText())
                        .header("Authorization", bearer(staffToken))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request(
                                "Forbidden Update", "PROTECTED", ServiceMode.WALK_IN, 0))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_ROLE"));
    }

    @Test
    void ownerUpdatesService() throws Exception {
        OwnerSession owner = registerOwner("update-service");
        JsonNode created = createService(owner.token(), request(
                "Original", "ORIGINAL", ServiceMode.WALK_IN, 5));
        ServiceUpsertRequest update = new ServiceUpsertRequest(
                "Updated Service",
                " Updated description ",
                "updated-code",
                ServiceMode.BOTH,
                25,
                15,
                1);

        mockMvc.perform(put("/api/services/{id}", created.path("id").asText())
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(created.path("id").asText()))
                .andExpect(jsonPath("$.data.name").value("Updated Service"))
                .andExpect(jsonPath("$.data.description").value("Updated description"))
                .andExpect(jsonPath("$.data.serviceCode").value("UPDATED-CODE"))
                .andExpect(jsonPath("$.data.serviceMode").value("BOTH"));
    }

    @Test
    void ownerDeactivatesServiceAndDefaultListExcludesIt() throws Exception {
        OwnerSession owner = registerOwner("deactivate-service");
        JsonNode service = createService(owner.token(), request(
                "Deactivate Me", "DEACTIVATE", ServiceMode.WALK_IN, 0));

        mockMvc.perform(patch("/api/services/{id}/status", service.path("id").asText())
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ServiceStatusRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
        mockMvc.perform(get("/api/services").header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/services")
                        .param("active", "false")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(service.path("id").asText()));
    }

    @Test
    void publicApiReturnsOnlyActiveServicesWithoutInternalConfiguration() throws Exception {
        OwnerSession owner = registerOwner("public-services");
        JsonNode active = createService(owner.token(), request(
                "Public Service", "PUBLIC", ServiceMode.BOTH, 0));
        JsonNode inactive = createService(owner.token(), request(
                "Hidden Service", "HIDDEN", ServiceMode.WALK_IN, 1));
        changeServiceStatus(owner.token(), inactive.path("id").asText(), false);

        mockMvc.perform(get("/api/public/businesses/{code}/services", owner.publicQueueCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].serviceId").value(active.path("id").asText()))
                .andExpect(jsonPath("$.data[0].supportsWalkIn").value(true))
                .andExpect(jsonPath("$.data[0].supportsAppointment").value(true))
                .andExpect(jsonPath("$.data[0].serviceCode").doesNotExist())
                .andExpect(jsonPath("$.data[0].appointmentSlotDurationMinutes").doesNotExist());
    }

    @Test
    void ownerCreatesValidSchedule() throws Exception {
        OwnerSession owner = registerOwner("schedule-create");
        JsonNode service = createService(owner.token(), request(
                "Both Service", "BOTH-SCHEDULE", ServiceMode.BOTH, 0));

        JsonNode schedule = createSchedule(
                owner.token(), service.path("id").asText(), schedule(1, 9, 12, true, true));

        assertThat(schedule.path("dayOfWeek").asInt()).isEqualTo(1);
        assertThat(schedule.path("queueOpenBeforeMinutes").asInt()).isEqualTo(30);
        assertThat(schedule.path("active").asBoolean()).isTrue();
    }

    @Test
    void overlappingScheduleIsRejected() throws Exception {
        OwnerSession owner = registerOwner("schedule-overlap");
        JsonNode service = createService(owner.token(), request(
                "Overlap Service", "OVERLAP", ServiceMode.BOTH, 0));
        String serviceId = service.path("id").asText();
        createSchedule(owner.token(), serviceId, schedule(2, 9, 12, true, true));

        mockMvc.perform(post("/api/services/{id}/schedules", serviceId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                scheduleAt(2, LocalTime.of(11, 0), LocalTime.of(14, 0), true, true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_OVERLAP"));
    }

    @Test
    void invalidDayOrTimeRangeIsRejected() throws Exception {
        OwnerSession owner = registerOwner("invalid-schedule");
        JsonNode service = createService(owner.token(), request(
                "Schedule Validation", "SCHEDULE-VALID", ServiceMode.BOTH, 0));
        String path = "/api/services/" + service.path("id").asText() + "/schedules";

        mockMvc.perform(post(path)
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(schedule(8, 9, 12, true, true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post(path)
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(schedule(1, 12, 9, true, true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIME_RANGE"));
    }

    @Test
    void walkInServiceCannotEnableAppointments() throws Exception {
        OwnerSession owner = registerOwner("walkin-mode");
        JsonNode service = createService(owner.token(), request(
                "Walk-in Only", "WALKIN-ONLY", ServiceMode.WALK_IN, 0));

        mockMvc.perform(post("/api/services/{id}/schedules", service.path("id").asText())
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(schedule(1, 9, 12, true, true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INCOMPATIBLE_SERVICE_MODE"));
    }

    @Test
    void appointmentServiceCannotEnableWalkIns() throws Exception {
        OwnerSession owner = registerOwner("appointment-mode");
        JsonNode service = createService(owner.token(), request(
                "Appointment Only", "APPOINTMENT-ONLY", ServiceMode.APPOINTMENT, 0));

        mockMvc.perform(post("/api/services/{id}/schedules", service.path("id").asText())
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(schedule(1, 9, 12, true, true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INCOMPATIBLE_SERVICE_MODE"));
    }

    @Test
    void staffCanViewSchedulesInConfiguredOrder() throws Exception {
        OwnerSession owner = registerOwner("staff-schedules");
        JsonNode service = createService(owner.token(), request(
                "Staff Schedule View", "STAFF-SCHEDULE", ServiceMode.BOTH, 0));
        String serviceId = service.path("id").asText();
        createSchedule(owner.token(), serviceId, schedule(3, 14, 17, true, true));
        createSchedule(owner.token(), serviceId, schedule(1, 9, 12, true, true));
        String staffToken = createAndLoginStaff(owner.token(), uniqueEmail("schedule-staff"));

        mockMvc.perform(get("/api/services/{id}/schedules", serviceId)
                        .header("Authorization", bearer(staffToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].dayOfWeek").value(1))
                .andExpect(jsonPath("$.data[1].dayOfWeek").value(3));
    }

    @Test
    void ownerUpdatesSchedule() throws Exception {
        OwnerSession owner = registerOwner("update-schedule");
        JsonNode service = createService(owner.token(), request(
                "Update Schedule", "UPDATE-SCHEDULE", ServiceMode.BOTH, 0));
        String serviceId = service.path("id").asText();
        JsonNode created = createSchedule(owner.token(), serviceId, schedule(1, 9, 12, true, true));

        mockMvc.perform(put("/api/services/{serviceId}/schedules/{scheduleId}",
                                serviceId, created.path("id").asText())
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                scheduleAt(4, LocalTime.of(10, 0), LocalTime.of(13, 0), false, true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dayOfWeek").value(4))
                .andExpect(jsonPath("$.data.appointmentBookingEnabled").value(false))
                .andExpect(jsonPath("$.data.walkInEnabled").value(true));
    }

    @Test
    void crossBusinessAccessIsBlocked() throws Exception {
        OwnerSession first = registerOwner("cross-first");
        OwnerSession second = registerOwner("cross-second");
        JsonNode secondService = createService(second.token(), request(
                "Second Business Service", "SECOND-BUSINESS", ServiceMode.WALK_IN, 0));

        mockMvc.perform(get("/api/services/{id}", secondService.path("id").asText())
                        .header("Authorization", bearer(first.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_BUSINESS_ACCESS_FORBIDDEN"));
        mockMvc.perform(post("/api/services/{id}/schedules", secondService.path("id").asText())
                        .header("Authorization", bearer(first.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(schedule(1, 9, 12, false, true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_BUSINESS_ACCESS_FORBIDDEN"));
    }

    @Test
    void inactiveSchedulesAreExcludedFromOverlapChecks() throws Exception {
        OwnerSession owner = registerOwner("inactive-schedule");
        JsonNode service = createService(owner.token(), request(
                "Inactive Schedule", "INACTIVE-SCHEDULE", ServiceMode.BOTH, 0));
        String serviceId = service.path("id").asText();
        JsonNode first = createSchedule(owner.token(), serviceId, schedule(5, 9, 12, true, true));

        mockMvc.perform(patch("/api/services/{serviceId}/schedules/{scheduleId}/status",
                                serviceId, first.path("id").asText())
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ScheduleStatusRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
        createSchedule(owner.token(), serviceId,
                scheduleAt(5, LocalTime.of(10, 0), LocalTime.of(11, 0), true, true));
        mockMvc.perform(get("/api/services/{id}/schedules", serviceId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].active").value(false));
    }

    private OwnerSession registerOwner(String key) throws Exception {
        String email = uniqueEmail(key + "-owner");
        MvcResult result = mockMvc.perform(post("/api/auth/register-owner")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RegisterOwnerRequest(
                                "Owner " + key,
                                "Business " + key + " " + UUID.randomUUID(),
                                email,
                                "OwnerPassword123",
                                "Asia/Kolkata"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode data = responseData(result);
        UUID businessId = UUID.fromString(data.path("business").path("id").asText());
        queueSessionRepository.deleteAll(queueSessionRepository.findByBusinessIdAndBusinessDate(
                businessId, LocalDate.now(ZoneId.of("Asia/Kolkata"))));
        queueSessionRepository.flush();
        queueConfigurationRepository.deleteAll(
                queueConfigurationRepository.findByBusinessIdAndStatusNotOrderByNameAsc(
                        businessId, QueueStatus.ARCHIVED));
        queueConfigurationRepository.flush();
        businessServiceRepository.deleteAll(
                businessServiceRepository.findByBusinessIdAndActiveTrueOrderByDisplayOrderAscNameAsc(
                        businessId));
        businessServiceRepository.flush();
        return new OwnerSession(
                data.path("accessToken").asText(),
                data.path("business").path("publicQueueCode").asText());
    }

    private JsonNode createService(String token, ServiceUpsertRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/services")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseData(result);
    }

    private JsonNode createSchedule(String token, String serviceId, ScheduleUpsertRequest request)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/services/{id}/schedules", serviceId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseData(result);
    }

    private String createAndLoginStaff(String ownerToken, String email) throws Exception {
        mockMvc.perform(post("/api/staff")
                        .header("Authorization", bearer(ownerToken))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateStaffRequest(
                                "Service Staff", email, "+919800000002", "Temporary123"))))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(email, "Temporary123"))))
                .andExpect(status().isOk())
                .andReturn();
        return responseData(login).path("accessToken").asText();
    }

    private void changeServiceStatus(String token, String serviceId, boolean active)
            throws Exception {
        mockMvc.perform(patch("/api/services/{id}/status", serviceId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ServiceStatusRequest(active))))
                .andExpect(status().isOk());
    }

    private static ServiceUpsertRequest request(
            String name, String code, ServiceMode mode, int displayOrder) {
        return new ServiceUpsertRequest(
                name,
                "Service description",
                code,
                mode,
                20,
                mode == ServiceMode.WALK_IN ? null : 30,
                displayOrder);
    }

    private static ScheduleUpsertRequest schedule(
            int day, int startHour, int endHour, boolean appointments, boolean walkIns) {
        return scheduleAt(
                day, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0), appointments, walkIns);
    }

    private static ScheduleUpsertRequest scheduleAt(
            int day,
            LocalTime start,
            LocalTime end,
            boolean appointments,
            boolean walkIns) {
        return new ScheduleUpsertRequest(day, start, end, 30, appointments, walkIns);
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private record OwnerSession(String token, String publicQueueCode) {
    }
}
