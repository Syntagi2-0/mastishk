package com.syntagi.queue.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QueueSessionScheduler {

    private final QueueSessionProvisioningService provisioningService;
    private final QueueTimeService timeService;

    public QueueSessionScheduler(
            QueueSessionProvisioningService provisioningService,
            QueueTimeService timeService) {
        this.provisioningService = provisioningService;
        this.timeService = timeService;
    }

    @Scheduled(cron = "${syntagi.queue.scheduler.cron:0 * * * * *}")
    public void createScheduledQueueSessions() {
        provisioningService.createDueSessions(timeService.now());
    }
}
