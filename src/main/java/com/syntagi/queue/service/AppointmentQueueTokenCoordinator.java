package com.syntagi.queue.service;

import com.syntagi.queue.entity.QueueSession;

/** Module-facing appointment integration used by queue session provisioning. */
public interface AppointmentQueueTokenCoordinator {

    int generateFor(QueueSession session);
}
