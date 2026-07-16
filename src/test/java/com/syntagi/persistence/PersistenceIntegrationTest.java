package com.syntagi.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.syntagi.appointment.entity.Appointment;
import com.syntagi.appointment.repository.AppointmentRepository;
import com.syntagi.appointment.repository.AppointmentSlotRepository;
import com.syntagi.auth.entity.User;
import com.syntagi.auth.repository.UserRepository;
import com.syntagi.business.entity.Business;
import com.syntagi.business.repository.BusinessRepository;
import com.syntagi.customer.entity.Customer;
import com.syntagi.customer.repository.CustomerRepository;
import com.syntagi.queue.entity.QueueSession;
import com.syntagi.queue.entity.QueueToken;
import com.syntagi.queue.enums.QueueTokenSourceType;
import com.syntagi.queue.repository.QueueSessionRepository;
import com.syntagi.queue.repository.QueueTokenRepository;
import com.syntagi.servicecatalog.entity.BusinessService;
import com.syntagi.servicecatalog.enums.ServiceMode;
import com.syntagi.servicecatalog.repository.BusinessServiceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Transactional
@ActiveProfiles("test")
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PersistenceIntegrationTest {

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

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final CustomerRepository customerRepository;
    private final BusinessServiceRepository businessServiceRepository;
    private final AppointmentSlotRepository appointmentSlotRepository;
    private final AppointmentRepository appointmentRepository;
    private final QueueSessionRepository queueSessionRepository;
    private final QueueTokenRepository queueTokenRepository;
    private final EntityManager entityManager;
    private final Flyway flyway;

    @Autowired
    PersistenceIntegrationTest(
            UserRepository userRepository,
            BusinessRepository businessRepository,
            CustomerRepository customerRepository,
            BusinessServiceRepository businessServiceRepository,
            AppointmentSlotRepository appointmentSlotRepository,
            AppointmentRepository appointmentRepository,
            QueueSessionRepository queueSessionRepository,
            QueueTokenRepository queueTokenRepository,
            EntityManager entityManager,
            Flyway flyway) {
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.customerRepository = customerRepository;
        this.businessServiceRepository = businessServiceRepository;
        this.appointmentSlotRepository = appointmentSlotRepository;
        this.appointmentRepository = appointmentRepository;
        this.queueSessionRepository = queueSessionRepository;
        this.queueTokenRepository = queueTokenRepository;
        this.entityManager = entityManager;
        this.flyway = flyway;
    }

    @Test
    void userEmailLookupIsCaseInsensitiveAndEmailIsNormalized() {
        User user = userRepository.saveAndFlush(
                new User("Owner", "  Owner@Example.COM ", null, "encoded-password"));

        assertThat(user.getEmail()).isEqualTo("owner@example.com");
        assertThat(userRepository.findByEmailIgnoreCase("OWNER@EXAMPLE.COM"))
                .contains(user);
    }

    @Test
    void businessSlugLookupIsCaseInsensitiveAndSlugIsNormalized() {
        Business business = businessRepository.saveAndFlush(
                new Business("Clinic", "  My-Clinic ", "CLINIC", "MY-CLINIC-Q"));

        assertThat(business.getSlug()).isEqualTo("my-clinic");
        assertThat(businessRepository.findBySlugIgnoreCase("MY-CLINIC")).contains(business);
    }

    @Test
    void duplicateBusinessSlugIsRejectedCaseInsensitively() {
        businessRepository.saveAndFlush(
                new Business("Clinic One", "clinic", "CLINIC", "CLINIC-ONE-Q"));

        assertThatThrownBy(() -> businessRepository.saveAndFlush(
                        new Business("Clinic Two", "CLINIC", "CLINIC", "CLINIC-TWO-Q")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateCustomerMobileWithinBusinessIsRejected() {
        Fixture fixture = createFixture("customer-unique");
        customerRepository.saveAndFlush(
                new Customer(fixture.business(), "First", "9000000001", null));

        assertThatThrownBy(() -> customerRepository.saveAndFlush(
                        new Customer(fixture.business(), "Second", "9000000001", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateServiceCodeWithinBusinessIsRejected() {
        Business business = businessRepository.saveAndFlush(
                new Business("Salon", "service-unique", "SALON", "SERVICE-UNIQUE-Q"));
        businessServiceRepository.saveAndFlush(
                new BusinessService(business, "Haircut", "HAIR", ServiceMode.BOTH));

        assertThatThrownBy(() -> businessServiceRepository.saveAndFlush(
                        new BusinessService(business, "Styling", "HAIR", ServiceMode.BOTH)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void confirmedAppointmentsAreOrderedByScheduledStartTime() {
        Fixture fixture = createFixture("appointment-order");
        Appointment later = appointmentRepository.save(newAppointment(
                fixture, "BOOK-LATER", LocalTime.of(11, 0)));
        Appointment earlier = appointmentRepository.save(newAppointment(
                fixture, "BOOK-EARLIER", LocalTime.of(9, 0)));
        appointmentRepository.flush();

        List<Appointment> results = appointmentRepository.findConfirmedByServiceAndDate(
                fixture.service().getId(), fixture.date());

        assertThat(results).containsExactly(earlier, later);
    }

    @Test
    void queueSessionServiceDateIsUnique() {
        Fixture fixture = createFixture("session-unique");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        queueSessionRepository.saveAndFlush(new QueueSession(
                fixture.business(), fixture.service(), null, fixture.date(), now));

        assertThatThrownBy(() -> queueSessionRepository.saveAndFlush(new QueueSession(
                        fixture.business(), fixture.service(), null, fixture.date(), now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void queueSessionCanBeLoadedWithPessimisticWriteLock() {
        Fixture fixture = createFixture("session-lock");
        QueueSession session = queueSessionRepository.saveAndFlush(new QueueSession(
                fixture.business(),
                fixture.service(),
                null,
                fixture.date(),
                OffsetDateTime.now(ZoneOffset.UTC)));
        entityManager.clear();

        QueueSession locked = queueSessionRepository.findByIdForUpdate(session.getId()).orElseThrow();

        assertThat(entityManager.getLockMode(locked)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void onlyOneQueueTokenCanReferenceAnAppointment() {
        Fixture fixture = createFixture("token-appointment-unique");
        Appointment appointment = appointmentRepository.saveAndFlush(
                newAppointment(fixture, "BOOK-TOKEN", LocalTime.of(9, 0)));
        QueueSession session = queueSessionRepository.saveAndFlush(new QueueSession(
                fixture.business(),
                fixture.service(),
                null,
                fixture.date(),
                OffsetDateTime.now(ZoneOffset.UTC)));
        queueTokenRepository.saveAndFlush(newToken(
                fixture, session, appointment, "A001", 1, 1, 0, LocalTime.of(9, 0)));

        assertThatThrownBy(() -> queueTokenRepository.saveAndFlush(newToken(
                        fixture, session, appointment, "A002", 2, 2, 0, LocalTime.of(9, 30))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nextTokenCandidatesUsePriorityThenQueueOrder() {
        Fixture fixture = createFixture("next-order");
        QueueSession session = queueSessionRepository.saveAndFlush(new QueueSession(
                fixture.business(),
                fixture.service(),
                null,
                fixture.date(),
                OffsetDateTime.now(ZoneOffset.UTC)));
        QueueToken unscheduled = queueTokenRepository.save(newToken(
                fixture, session, null, "W001", 1, 1, 0, null));
        QueueToken laterHighPriority = queueTokenRepository.save(newToken(
                fixture, session, null, "W002", 2, 2, 10, LocalTime.of(10, 0)));
        QueueToken earlierHighPriority = queueTokenRepository.save(newToken(
                fixture, session, null, "W003", 3, 3, 10, LocalTime.of(9, 0)));
        queueTokenRepository.flush();

        List<QueueToken> candidates = queueTokenRepository.findNextCandidates(
                session.getId(), PageRequest.of(0, 10));

        assertThat(candidates)
                .containsExactly(laterHighPriority, earlierHighPriority, unscheduled);
    }

    @Test
    void flywayAndHibernateValidateTheCompleteSchema() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("8");
        assertThat(appointmentSlotRepository.count()).isZero();
    }

    private Fixture createFixture(String key) {
        Business business = businessRepository.saveAndFlush(new Business(
                "Business " + key, key, "GENERIC", key.toUpperCase() + "-Q"));
        BusinessService service = businessServiceRepository.saveAndFlush(
                new BusinessService(business, "Service", "SVC", ServiceMode.BOTH));
        Customer customer = customerRepository.saveAndFlush(
                new Customer(business, "Customer", "9" + uniqueDigits(key), null));
        return new Fixture(business, service, customer, LocalDate.of(2030, 1, 10));
    }

    private static Appointment newAppointment(
            Fixture fixture, String bookingReference, LocalTime startTime) {
        return new Appointment(
                fixture.business(),
                fixture.service(),
                fixture.customer(),
                null,
                bookingReference,
                fixture.date(),
                startTime,
                startTime.plusMinutes(30),
                null);
    }

    private static QueueToken newToken(
            Fixture fixture,
            QueueSession session,
            Appointment appointment,
            String display,
            int number,
            long order,
            int priority,
            LocalTime scheduledTime) {
        return new QueueToken(
                session,
                fixture.business(),
                fixture.service(),
                fixture.customer(),
                appointment,
                number,
                display,
                appointment == null
                        ? QueueTokenSourceType.WALK_IN
                        : QueueTokenSourceType.APPOINTMENT,
                scheduledTime,
                OffsetDateTime.now(ZoneOffset.UTC),
                order,
                priority,
                null);
    }

    private static String uniqueDigits(String value) {
        long positiveHash = Integer.toUnsignedLong(value.hashCode());
        return String.format("%09d", positiveHash % 1_000_000_000L);
    }

    private record Fixture(
            Business business,
            BusinessService service,
            Customer customer,
            LocalDate date) {
    }
}
