package com.syntagi.queue.service;

import com.syntagi.queue.entity.QueueSession;
import com.syntagi.queue.repository.QueueSessionRepository;
import com.syntagi.servicecatalog.entity.ServiceSchedule;
import com.syntagi.servicecatalog.repository.ServiceScheduleRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class QueueSessionProvisioningService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            QueueSessionProvisioningService.class);

    private final ServiceScheduleRepository scheduleRepository;
    private final QueueSessionRepository sessionRepository;
    private final TransactionTemplate transactionTemplate;
    private final AppointmentQueueTokenCoordinator appointmentTokenCoordinator;

    public QueueSessionProvisioningService(
            ServiceScheduleRepository scheduleRepository,
            QueueSessionRepository sessionRepository,
            PlatformTransactionManager transactionManager,
            AppointmentQueueTokenCoordinator appointmentTokenCoordinator) {
        this.scheduleRepository = scheduleRepository;
        this.sessionRepository = sessionRepository;
        this.appointmentTokenCoordinator = appointmentTokenCoordinator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public int createDueSessions(Instant now) {
        int created = 0;
        for (ServiceSchedule schedule : scheduleRepository.findAllActiveForQueueScheduler()) {
            DueSession due = dueSession(schedule, now);
            if (due == null) {
                continue;
            }
            try {
                Boolean wasCreated = transactionTemplate.execute(status -> {
                    if (sessionRepository.existsByBusinessServiceIdAndBusinessDate(
                            schedule.getBusinessService().getId(), due.businessDate())) {
                        return false;
                    }
                    QueueSession session = sessionRepository.saveAndFlush(new QueueSession(
                            schedule.getBusinessService().getBusiness(),
                            schedule.getBusinessService(),
                            schedule,
                            due.businessDate(),
                            now.atOffset(ZoneOffset.UTC)));
                    appointmentTokenCoordinator.generateFor(session);
                    return true;
                });
                if (Boolean.TRUE.equals(wasCreated)) {
                    created++;
                }
            } catch (DataIntegrityViolationException race) {
                LOGGER.debug("Queue session was created concurrently for service {} and date {}",
                        schedule.getBusinessService().getId(), due.businessDate());
            }
        }
        return created;
    }

    private static DueSession dueSession(ServiceSchedule schedule, Instant now) {
        ZoneId zone = ZoneId.of(schedule.getBusinessService().getBusiness().getTimezone());
        ZonedDateTime businessNow = now.atZone(zone);
        LocalDate businessDate = businessNow.toLocalDate();
        if (businessDate.getDayOfWeek() != schedule.getDayOfWeek()) {
            return null;
        }
        LocalDateTime opening = businessDate.atTime(schedule.getOperatingStartTime())
                .minusMinutes(schedule.getQueueOpenBeforeMinutes());
        LocalDateTime closing = businessDate.atTime(schedule.getOperatingEndTime());
        LocalDateTime localNow = businessNow.toLocalDateTime();
        return !localNow.isBefore(opening) && localNow.isBefore(closing)
                ? new DueSession(businessDate)
                : null;
    }

    private record DueSession(LocalDate businessDate) {
    }
}
