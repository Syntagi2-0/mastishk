package com.syntagi.queue.service;

import com.syntagi.queue.entity.QueueToken;

/** Queue-facing notification contract that avoids coupling queue workflows to notification storage. */
public interface QueueNotificationCoordinator {

    void tokenCalled(QueueToken token);

    void tokenSkipped(QueueToken token);

    void tokenCancelled(QueueToken token);
}
