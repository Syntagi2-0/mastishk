package com.syntagi.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syntagi.auth.dto.request.RegisterOwnerRequest;
import com.syntagi.customer.entity.Customer;
import com.syntagi.customer.repository.CustomerRepository;
import com.syntagi.queue.dto.request.WalkInRequest;
import com.syntagi.queue.dto.request.CreateQueueSessionRequest;
import com.syntagi.queue.dto.request.QueueUpsertRequest;
import com.syntagi.queue.entity.QueueConfiguration;
import com.syntagi.queue.entity.QueueSession;
import com.syntagi.queue.entity.QueueToken;
import com.syntagi.queue.enums.QueueSessionStatus;
import com.syntagi.queue.enums.QueueStatus;
import com.syntagi.queue.enums.QueueTokenSourceType;
import com.syntagi.queue.enums.QueueTokenStatus;
import com.syntagi.queue.repository.QueueSessionRepository;
import com.syntagi.queue.repository.QueueConfigurationRepository;
import com.syntagi.queue.repository.QueueTokenRepository;
import com.syntagi.queue.service.QueueSessionProvisioningService;
import com.syntagi.servicecatalog.dto.request.ServiceUpsertRequest;
import com.syntagi.servicecatalog.entity.BusinessService;
import com.syntagi.servicecatalog.entity.ServiceSchedule;
import com.syntagi.servicecatalog.enums.ServiceMode;
import com.syntagi.servicecatalog.repository.BusinessServiceRepository;
import com.syntagi.servicecatalog.repository.ServiceScheduleRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
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
class QueueManagementIntegrationTest {

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

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BusinessServiceRepository serviceRepository;
    @Autowired private ServiceScheduleRepository scheduleRepository;
    @Autowired private QueueSessionRepository sessionRepository;
    @Autowired private QueueConfigurationRepository queueConfigurationRepository;
    @Autowired private QueueTokenRepository tokenRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private QueueSessionProvisioningService provisioningService;

    @Test
    void schedulerCreatesTodaysQueueSessionOnlyOnce() throws Exception {
        QueueSetup setup = createScheduledQueue("scheduler");

        int firstRun = provisioningService.createDueSessions(Instant.now());
        int secondRun = provisioningService.createDueSessions(Instant.now());

        assertThat(firstRun).isEqualTo(1);
        assertThat(secondRun).isZero();
        QueueSession session = findSession(setup);
        assertThat(session.getStatus()).isEqualTo(QueueSessionStatus.OPEN);
        assertThat(session.getTokenCounter()).isZero();
        assertThat(session.getWalkInTokenCounter()).isZero();
        assertThat(session.getCurrentToken()).isNull();
        assertThat(session.getOpenedAt()).isNotNull();
    }

    @Test
    void queueConfigurationLifecycleAndSessionPauseResumeAreEnforced() throws Exception {
        QueueSetup setup = createScheduledQueue("configuration-lifecycle");
        JsonNode queue = createQueueConfiguration(setup, "Front Desk");
        String queueId = queue.path("id").asText();
        assertThat(queue.path("status").asText()).isEqualTo("DRAFT");

        mockMvc.perform(post("/api/queues/{queueId}/sessions", queueId)
                        .header("Authorization", bearer(setup.ownerToken()))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUEUE_NOT_ACTIVE"));

        mockMvc.perform(post("/api/queues/{queueId}/activate", queueId)
                        .header("Authorization", bearer(setup.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        JsonNode session = responseData(mockMvc.perform(
                        post("/api/queues/{queueId}/sessions", queueId)
                                .header("Authorization", bearer(setup.ownerToken()))
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andReturn());
        String sessionId = session.path("queueSessionId").asText();

        mockMvc.perform(post("/api/queues/{queueId}/sessions", queueId)
                        .header("Authorization", bearer(setup.ownerToken()))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_QUEUE_SESSION"));

        mockMvc.perform(post("/api/queue-sessions/{sessionId}/pause", sessionId)
                        .header("Authorization", bearer(setup.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"));
        mockMvc.perform(post("/api/queue/next")
                        .header("Authorization", bearer(setup.ownerToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUEUE_SESSION_CLOSED"));
        mockMvc.perform(post("/api/public/businesses/{code}/walk-in", setup.publicQueueCode())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new WalkInRequest(
                                setup.service().getId(), "Paused", "+919911110001"))))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/queue-sessions/{sessionId}/resume", sessionId)
                        .header("Authorization", bearer(setup.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"));
        walkIn(setup, "Open Customer", "+919911110002");

        mockMvc.perform(post("/api/queues/{queueId}/archive", queueId)
                        .header("Authorization", bearer(setup.ownerToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_QUEUE_SESSION_EXISTS"));
        mockMvc.perform(post("/api/queue-sessions/{sessionId}/close", sessionId)
                        .header("Authorization", bearer(setup.ownerToken())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/queues/{queueId}/close", queueId)
                        .header("Authorization", bearer(setup.ownerToken())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/queues/{queueId}/archive", queueId)
                        .header("Authorization", bearer(setup.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
    }

    @Test
    void queueConfigurationRejectsForeignAndAppointmentOnlyServices() throws Exception {
        QueueSetup first = createScheduledQueue("queue-owner-one");
        QueueSetup second = createScheduledQueue("queue-owner-two");
        mockMvc.perform(post("/api/queues")
                        .header("Authorization", bearer(first.ownerToken()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new QueueUpsertRequest(
                                "Foreign Queue", second.service().getId()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_BUSINESS_ACCESS_FORBIDDEN"));

        JsonNode secondQueue = createQueueConfiguration(second, "Second Business Queue");
        mockMvc.perform(get("/api/queues/{queueId}", secondQueue.path("id").asText())
                        .header("Authorization", bearer(first.ownerToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_BUSINESS_ACCESS_FORBIDDEN"));

        scheduleRepository.deleteAll(
                scheduleRepository.findByBusinessServiceIdAndActiveTrue(first.service().getId()));
        first.service().updateMode(ServiceMode.APPOINTMENT);
        serviceRepository.saveAndFlush(first.service());
        mockMvc.perform(post("/api/queues")
                        .header("Authorization", bearer(first.ownerToken()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new QueueUpsertRequest(
                                "Appointments", first.service().getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WALK_IN_NOT_SUPPORTED"));
        deleteSchedules(second);
    }

    @Test
    void createdSessionDoesNotAcceptCustomers() throws Exception {
        QueueSetup setup = createScheduledQueue("created-session");
        JsonNode createdQueue = createQueueConfiguration(setup, "Created State");
        UUID queueId = UUID.fromString(createdQueue.path("id").asText());
        mockMvc.perform(post("/api/queues/{queueId}/activate", queueId)
                        .header("Authorization", bearer(setup.ownerToken())))
                .andExpect(status().isOk());
        QueueConfiguration queue = queueConfigurationRepository.findById(queueId).orElseThrow();
        LocalDate date = LocalDate.now(ZoneId.of(setup.service().getBusiness().getTimezone()));
        sessionRepository.saveAndFlush(QueueSession.created(
                queue, null, date, LocalTime.of(9, 0), LocalTime.of(18, 0),
                OffsetDateTime.now(ZoneOffset.UTC)));

        mockMvc.perform(post("/api/public/businesses/{code}/walk-in", setup.publicQueueCode())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new WalkInRequest(
                                setup.service().getId(), "Created", "+919911110003"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUEUE_SESSION_NOT_FOUND"));
    }

    @Test
    void ownerCanCreateTodaysQueueManuallyUsingScheduleDefaults() throws Exception {
        QueueSetup setup = createScheduledQueue("manual-scheduled");

        mockMvc.perform(post("/api/queue-sessions")
                        .header("Authorization", bearer(setup.ownerToken()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CreateQueueSessionRequest(setup.service().getId(), null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.queueSessionId").isNotEmpty())
                .andExpect(jsonPath("$.data.serviceId").value(setup.service().getId().toString()))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.openingTime").value("00:00:00"))
                .andExpect(jsonPath("$.data.closingTime").value("23:59:59"));

        QueueSession session = findSession(setup);
        assertThat(session.getServiceSchedule()).isNotNull();
        assertThat(session.getStatus()).isEqualTo(QueueSessionStatus.OPEN);
    }

    @Test
    void manualQueueCreationPreventsDuplicates() throws Exception {
        QueueSetup setup = createScheduledQueue("manual-duplicate");
        CreateQueueSessionRequest request =
                new CreateQueueSessionRequest(setup.service().getId(), null, null);

        createQueueSession(setup, request).andExpect(status().isCreated());
        createQueueSession(setup, request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_QUEUE_SESSION"));
    }

    @Test
    void manualQueueCreationRejectsServiceFromAnotherBusiness() throws Exception {
        QueueSetup first = createScheduledQueue("manual-owner-first");
        QueueSetup second = createScheduledQueue("manual-owner-second");

        mockMvc.perform(post("/api/queue-sessions")
                        .header("Authorization", bearer(first.ownerToken()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateQueueSessionRequest(
                                second.service().getId(), null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_BUSINESS_ACCESS_FORBIDDEN"));
        deleteSchedules(first);
        deleteSchedules(second);
    }

    @Test
    void manualQueueCreationRejectsAppointmentOnlyService() throws Exception {
        QueueSetup setup = createScheduledQueue("manual-appointment-only");
        scheduleRepository.deleteAll(
                scheduleRepository.findByBusinessServiceIdAndActiveTrue(setup.service().getId()));
        setup.service().updateMode(ServiceMode.APPOINTMENT);
        serviceRepository.saveAndFlush(setup.service());

        createQueueSession(
                        setup,
                        new CreateQueueSessionRequest(setup.service().getId(), null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WALK_IN_NOT_SUPPORTED"));
    }

    @Test
    void manualQueueCanBeCreatedWithoutSchedule() throws Exception {
        QueueSetup setup = createScheduledQueue("manual-no-schedule");
        scheduleRepository.deleteAll(
                scheduleRepository.findByBusinessServiceIdAndActiveTrue(setup.service().getId()));

        createQueueSession(
                        setup,
                        new CreateQueueSessionRequest(setup.service().getId(), null, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.closingTime").doesNotExist());

        assertThat(findSession(setup).getServiceSchedule()).isNull();
    }

    @Test
    void manualQueueAcceptsOptionalOperatingTimes() throws Exception {
        QueueSetup setup = createScheduledQueue("manual-times");

        createQueueSession(
                        setup,
                        new CreateQueueSessionRequest(
                                setup.service().getId(), LocalTime.of(9, 30), LocalTime.of(18, 0)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.openingTime").value("09:30:00"))
                .andExpect(jsonPath("$.data.closingTime").value("18:00:00"));

        QueueSession session = findSession(setup);
        assertThat(session.getOpeningTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(session.getClosingTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(session.getServiceSchedule()).isNull();
    }

    @Test
    void manualQueueAcceptsOvernightOperatingTimeRange() throws Exception {
        QueueSetup setup = createScheduledQueue("manual-overnight-times");

        createQueueSession(
                        setup,
                        new CreateQueueSessionRequest(
                                setup.service().getId(), LocalTime.of(18, 0), LocalTime.of(9, 30)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.openingTime").value("18:00:00"))
                .andExpect(jsonPath("$.data.closingTime").value("09:30:00"));

        QueueSession session = findSession(setup);
        assertThat(session.getOpeningTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(session.getClosingTime()).isEqualTo(LocalTime.of(9, 30));
    }

    @Test
    void manualQueueRejectsEqualOperatingTimes() throws Exception {
        QueueSetup setup = createScheduledQueue("manual-equal-times");

        createQueueSession(
                        setup,
                        new CreateQueueSessionRequest(
                                setup.service().getId(), LocalTime.of(18, 0), LocalTime.of(18, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIME_RANGE"));
        deleteSchedules(setup);
    }

    @Test
    void closingQueueIsExplicitAndPreventsFurtherWalkIns() throws Exception {
        QueueSetup setup = createScheduledQueue("manual-close");
        JsonNode created = responseData(createQueueSession(
                        setup,
                        new CreateQueueSessionRequest(setup.service().getId(), null, null))
                .andExpect(status().isCreated())
                .andReturn());
        String sessionId = created.path("queueSessionId").asText();

        mockMvc.perform(post("/api/queue-sessions/{queueSessionId}/close", sessionId)
                        .header("Authorization", bearer(setup.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.closedAt").isNotEmpty());

        mockMvc.perform(post(
                        "/api/public/businesses/{code}/walk-in", setup.publicQueueCode())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new WalkInRequest(
                                setup.service().getId(), "Closed Customer", "+919900009999"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUEUE_SESSION_NOT_FOUND"));

        mockMvc.perform(post("/api/queue-sessions/{queueSessionId}/close", sessionId)
                        .header("Authorization", bearer(setup.ownerToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUEUE_SESSION_CLOSED"))
                .andExpect(jsonPath("$.message").value("Queue session is already closed"));
    }

    @Test
    void walkInCreatesTokenAndReusesCustomer() throws Exception {
        QueueSetup setup = readyQueue("walkin-reuse");

        JsonNode first = walkIn(setup, "Shared Customer", "+919900001111");
        JsonNode second = walkIn(setup, "Different Submitted Name", "+919900001111");

        assertThat(first.path("token").asText()).isEqualTo("W001");
        assertThat(second.path("token").asText()).isEqualTo("W002");
        assertThat(customerRepository.findByBusinessIdAndMobile(
                setup.service().getBusiness().getId(), "+919900001111")).isPresent();
        assertThat(tokenRepository.findLiveByQueueSessionId(findSession(setup).getId()))
                .hasSize(2)
                .extracting(token -> token.getCustomer().getId())
                .containsOnly(firstCustomerId(setup));
    }

    @Test
    void walkInTokenNumbersAndQueueOrderIncreaseSequentially() throws Exception {
        QueueSetup setup = readyQueue("token-sequence");
        walkIn(setup, "First", "+919900002001");
        walkIn(setup, "Second", "+919900002002");

        QueueToken first = token(setup, "W001");
        QueueToken second = token(setup, "W002");
        assertThat(first.getTokenNumber()).isEqualTo(1);
        assertThat(second.getTokenNumber()).isEqualTo(2);
        assertThat(first.getQueueOrder()).isEqualTo(1);
        assertThat(second.getQueueOrder()).isEqualTo(2);
    }

    @Test
    void nextFirstCallCallsFirstWaitingToken() throws Exception {
        QueueSetup setup = readyQueue("next-first");
        walkIn(setup, "First", "+919900003001");

        mockMvc.perform(post("/api/queue/next")
                        .header("Authorization", bearer(setup.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentToken").value("W001"))
                .andExpect(jsonPath("$.data.waitingCount").value(0));
        assertThat(token(setup, "W001").getStatus()).isEqualTo(QueueTokenStatus.CALLED);
    }

    @Test
    void nextSecondCallCompletesCurrentAndCallsNext() throws Exception {
        QueueSetup setup = readyQueue("next-second");
        walkIn(setup, "First", "+919900004001");
        walkIn(setup, "Second", "+919900004002");
        advance(setup);

        JsonNode response = advance(setup);

        assertThat(response.path("currentToken").asText()).isEqualTo("W002");
        assertThat(token(setup, "W001").getStatus()).isEqualTo(QueueTokenStatus.COMPLETED);
        assertThat(token(setup, "W001").getCompletedAt()).isNotNull();
        assertThat(token(setup, "W002").getStatus()).isEqualTo(QueueTokenStatus.CALLED);
    }

    @Test
    void nextCompletesLastCustomerAndLeavesQueueEmpty() throws Exception {
        QueueSetup setup = readyQueue("complete-last");
        walkIn(setup, "Only", "+919900005001");
        advance(setup);

        JsonNode response = advance(setup);

        assertThat(response.path("currentToken").isNull()).isTrue();
        assertThat(token(setup, "W001").getStatus()).isEqualTo(QueueTokenStatus.COMPLETED);
    }

    @Test
    void skipMarksCurrentSkippedAndCallsNext() throws Exception {
        QueueSetup setup = readyQueue("skip");
        walkIn(setup, "First", "+919900006001");
        walkIn(setup, "Second", "+919900006002");
        advance(setup);

        mockMvc.perform(post("/api/queue/current/skip")
                        .header("Authorization", bearer(setup.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentToken").value("W002"));
        assertThat(token(setup, "W001").getStatus()).isEqualTo(QueueTokenStatus.SKIPPED);
        assertThat(token(setup, "W002").getStatus()).isEqualTo(QueueTokenStatus.CALLED);
    }

    @Test
    void recallReturnsCurrentWithoutChangingState() throws Exception {
        QueueSetup setup = readyQueue("recall");
        walkIn(setup, "Recall", "+919900007001");
        advance(setup);
        OffsetDateTime calledAt = token(setup, "W001").getCalledAt();

        mockMvc.perform(post("/api/queue/current/recall")
                        .header("Authorization", bearer(setup.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentToken").value("W001"));
        QueueToken recalled = token(setup, "W001");
        assertThat(recalled.getStatus()).isEqualTo(QueueTokenStatus.CALLED);
        assertThat(recalled.getCalledAt()).isEqualTo(calledAt);
    }

    @Test
    void waitingQueueUsesPriorityDescendingThenQueueOrderAscending() throws Exception {
        QueueSetup setup = readyQueue("ordering");
        QueueSession session = findSession(setup);
        createToken(session, "W010", 10, 10, 0, "+919900008010");
        createToken(session, "W011", 11, 11, 5, "+919900008011");
        createToken(session, "W012", 12, 12, 5, "+919900008012");

        mockMvc.perform(get("/api/queue/waiting")
                        .header("Authorization", bearer(setup.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].token").value("W011"))
                .andExpect(jsonPath("$.data[1].token").value("W012"))
                .andExpect(jsonPath("$.data[2].token").value("W010"));
    }

    @Test
    void concurrentNextCallsAreSerializedByQueueSessionLock() throws Exception {
        QueueSetup setup = readyQueue("concurrent-next");
        walkIn(setup, "First", "+919900009001");
        walkIn(setup, "Second", "+919900009002");
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<String> next = () -> responseData(mockMvc.perform(post("/api/queue/next")
                            .header("Authorization", bearer(setup.ownerToken())))
                    .andExpect(status().isOk())
                    .andReturn()).path("currentToken").asText();
            Future<String> first = executor.submit(next);
            Future<String> second = executor.submit(next);

            assertThat(Set.of(first.get(), second.get())).isEqualTo(Set.of("W001", "W002"));
            assertThat(token(setup, "W001").getStatus()).isEqualTo(QueueTokenStatus.COMPLETED);
            assertThat(token(setup, "W002").getStatus()).isEqualTo(QueueTokenStatus.CALLED);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void liveQueueReturnsPublicStatusWithoutInternalIds() throws Exception {
        QueueSetup setup = readyQueue("live");
        walkIn(setup, "Live First", "+919900010001");
        walkIn(setup, "Live Second", "+919900010002");
        advance(setup);

        mockMvc.perform(get("/api/public/queue/W002")
                        .param("mobile", "+919900010002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("W002"))
                .andExpect(jsonPath("$.data.currentToken").value("W001"))
                .andExpect(jsonPath("$.data.waitingCustomers").value(1))
                .andExpect(jsonPath("$.data.estimatedPosition").value(1))
                .andExpect(jsonPath("$.data.estimatedWaitingCount").value(1))
                .andExpect(jsonPath("$.data.business").value(setup.service().getBusiness().getName()))
                .andExpect(jsonPath("$.data.service").value(setup.service().getName()))
                .andExpect(jsonPath("$.data.customerToken").value("W002"))
                .andExpect(jsonPath("$.data.customerStatus").value("WAITING"))
                .andExpect(jsonPath("$.data.customersAhead").value(1))
                .andExpect(jsonPath("$.data.estimatedWaitingTimeMinutes").value(10))
                .andExpect(jsonPath("$.data.queueStatus").value("OPEN"))
                .andExpect(jsonPath("$.data.serviceId").doesNotExist())
                .andExpect(jsonPath("$.data.customerId").doesNotExist())
                .andExpect(jsonPath("$.data.customerName").doesNotExist())
                .andExpect(jsonPath("$.data.mobile").doesNotExist());

        mockMvc.perform(get("/api/public/queue/W002")
                        .param("mobile", "+919900010099"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUEUE_TOKEN_NOT_FOUND"));
    }

    @Test
    void dashboardAndTodayQueueSummarizeOnlyTheAuthenticatedBusiness() throws Exception {
        QueueSetup first = readyQueue("dashboard-first");
        QueueSetup second = readyQueue("dashboard-second");
        walkIn(first, "Dashboard First", "+919900012001");
        walkIn(first, "Dashboard Second", "+919900012002");
        walkIn(second, "Other Tenant", "+919900012003");
        advance(first);

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", bearer(first.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.business.name")
                        .value(first.service().getBusiness().getName()))
                .andExpect(jsonPath("$.data.queue.currentToken").value("W001"))
                .andExpect(jsonPath("$.data.queue.currentCustomer").value("Dashboard First"))
                .andExpect(jsonPath("$.data.queue.waitingCount").value(1))
                .andExpect(jsonPath("$.data.queue.totalTokensToday").value(2))
                .andExpect(jsonPath("$.data.totalActiveServices").value(1));

        mockMvc.perform(get("/api/dashboard/today-queue")
                        .header("Authorization", bearer(first.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current[0].tokenDisplay").value("W001"))
                .andExpect(jsonPath("$.data.waiting[0].tokenDisplay").value("W002"))
                .andExpect(jsonPath("$.data.current.length()").value(1))
                .andExpect(jsonPath("$.data.waiting.length()").value(1));
    }

    @Test
    void qrBusinessAndSearchApisArePublicSafeAndTenantIsolated() throws Exception {
        QueueSetup first = readyQueue("qr-search-first");
        QueueSetup second = readyQueue("qr-search-second");
        walkIn(first, "Searchable Customer", "+919900013001");
        walkIn(second, "Other Searchable", "+919900013002");

        mockMvc.perform(get("/api/public/businesses/{code}", first.publicQueueCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.business").value(first.service().getBusiness().getName()))
                .andExpect(jsonPath("$.data.businessType").value("GENERAL"))
                .andExpect(jsonPath("$.data.queueStatus").value("OPEN"))
                .andExpect(jsonPath("$.data.availableServices[0].serviceId")
                        .value(first.service().getId().toString()))
                .andExpect(jsonPath("$.data.availableServices[0].queueStatus").value("OPEN"))
                .andExpect(jsonPath("$.data.internalId").doesNotExist());

        mockMvc.perform(get("/api/customers")
                        .param("search", "Searchable")
                        .header("Authorization", bearer(first.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].fullName").value("Searchable Customer"));

        mockMvc.perform(get("/api/services/search")
                        .param("search", "qr-search-first")
                        .header("Authorization", bearer(first.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.totalElements").value(1));
    }

    @Test
    void crossBusinessQueueAccessIsBlocked() throws Exception {
        QueueSetup first = readyQueue("cross-first");
        QueueSetup second = readyQueue("cross-second");

        mockMvc.perform(get("/api/queue/current")
                        .param("serviceId", second.service().getId().toString())
                        .header("Authorization", bearer(first.ownerToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_BUSINESS_ACCESS_FORBIDDEN"));
        mockMvc.perform(post("/api/public/businesses/{code}/walk-in", first.publicQueueCode())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new WalkInRequest(
                                second.service().getId(), "Wrong Business", "+919900011001"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"));
    }

    private QueueSetup readyQueue(String key) throws Exception {
        QueueSetup setup = createScheduledQueue(key);
        assertThat(provisioningService.createDueSessions(Instant.now())).isEqualTo(1);
        return setup;
    }

    private QueueSetup createScheduledQueue(String key) throws Exception {
        Owner owner = registerOwner(key);
        MvcResult result = mockMvc.perform(post("/api/services")
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ServiceUpsertRequest(
                                "Queue Service " + key,
                                "Queue test service",
                                "QUEUE-" + UUID.randomUUID(),
                                ServiceMode.WALK_IN,
                                10,
                                null,
                                0))))
                .andExpect(status().isCreated())
                .andReturn();
        BusinessService service = serviceRepository.findWithBusinessById(
                        UUID.fromString(responseData(result).path("id").asText()))
                .orElseThrow();
        LocalDate today = LocalDate.now(ZoneId.of(service.getBusiness().getTimezone()));
        ServiceSchedule schedule = new ServiceSchedule(
                service, today.getDayOfWeek(), LocalTime.MIN, LocalTime.of(23, 59, 59));
        schedule.configureQueueOpening(0);
        schedule.configureBookingModes(false, true);
        scheduleRepository.save(schedule);
        return new QueueSetup(owner.token(), owner.publicQueueCode(), service);
    }

    private Owner registerOwner(String key) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register-owner")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RegisterOwnerRequest(
                                "Queue Owner " + key,
                                "Queue Business " + key + " " + UUID.randomUUID(),
                                key + "-" + UUID.randomUUID() + "@example.com",
                                "OwnerPassword123",
                                "Asia/Kolkata"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode data = responseData(result);
        UUID businessId = UUID.fromString(data.path("business").path("id").asText());
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        sessionRepository.deleteAll(sessionRepository.findByBusinessIdAndBusinessDate(
                businessId, today));
        sessionRepository.flush();
        queueConfigurationRepository.deleteAll(
                queueConfigurationRepository.findByBusinessIdAndStatusNotOrderByNameAsc(
                        businessId, QueueStatus.ARCHIVED));
        queueConfigurationRepository.flush();
        serviceRepository.deleteAll(
                serviceRepository.findByBusinessIdAndActiveTrueOrderByDisplayOrderAscNameAsc(
                        businessId));
        serviceRepository.flush();
        return new Owner(
                data.path("accessToken").asText(),
                data.path("business").path("publicQueueCode").asText());
    }

    private JsonNode walkIn(QueueSetup setup, String fullName, String mobile) throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/public/businesses/{code}/walk-in", setup.publicQueueCode())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new WalkInRequest(setup.service().getId(), fullName, mobile))))
                .andExpect(status().isCreated())
                .andReturn();
        return responseData(result);
    }

    private JsonNode advance(QueueSetup setup) throws Exception {
        return responseData(mockMvc.perform(post("/api/queue/next")
                        .header("Authorization", bearer(setup.ownerToken())))
                .andExpect(status().isOk())
                .andReturn());
    }

    private org.springframework.test.web.servlet.ResultActions createQueueSession(
            QueueSetup setup, CreateQueueSessionRequest request) throws Exception {
        return mockMvc.perform(post("/api/queue-sessions")
                .header("Authorization", bearer(setup.ownerToken()))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)));
    }

    private JsonNode createQueueConfiguration(QueueSetup setup, String name) throws Exception {
        return responseData(mockMvc.perform(post("/api/queues")
                        .header("Authorization", bearer(setup.ownerToken()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new QueueUpsertRequest(name, setup.service().getId()))))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private void deleteSchedules(QueueSetup setup) {
        scheduleRepository.deleteAll(
                scheduleRepository.findByBusinessServiceIdAndActiveTrue(setup.service().getId()));
    }

    private QueueSession findSession(QueueSetup setup) {
        LocalDate date = LocalDate.now(ZoneId.of(setup.service().getBusiness().getTimezone()));
        return sessionRepository.findByBusinessServiceIdAndBusinessDate(
                setup.service().getId(), date).orElseThrow();
    }

    private QueueToken token(QueueSetup setup, String display) {
        return tokenRepository.findByQueueSessionIdAndTokenDisplay(
                findSession(setup).getId(), display).orElseThrow();
    }

    private UUID firstCustomerId(QueueSetup setup) {
        return token(setup, "W001").getCustomer().getId();
    }

    private void createToken(
            QueueSession session,
            String display,
            int number,
            long queueOrder,
            int priority,
            String mobile) {
        Customer customer = customerRepository.save(new Customer(
                session.getBusiness(), "Customer " + display, mobile, null));
        tokenRepository.save(new QueueToken(
                session,
                session.getBusiness(),
                session.getBusinessService(),
                customer,
                null,
                number,
                display,
                QueueTokenSourceType.WALK_IN,
                null,
                OffsetDateTime.now(ZoneOffset.UTC),
                queueOrder,
                priority,
                null));
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private record Owner(String token, String publicQueueCode) {
    }

    private record QueueSetup(
            String ownerToken, String publicQueueCode, BusinessService service) {
    }
}
