package com.syntagi.queue.service;

import com.syntagi.queue.entity.QueueToken;

/** Keeps queue-token lifecycle code independent from appointment persistence details. */
public interface AppointmentStatusCoordinator {

    void completed(QueueToken token);

    void noShow(QueueToken token);
}
