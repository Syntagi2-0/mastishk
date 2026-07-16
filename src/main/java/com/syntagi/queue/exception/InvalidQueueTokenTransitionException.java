package com.syntagi.queue.exception;

import com.syntagi.common.exception.ApplicationException;
import com.syntagi.common.exception.ErrorCode;
import com.syntagi.queue.enums.QueueTokenStatus;

public class InvalidQueueTokenTransitionException extends ApplicationException {

    public InvalidQueueTokenTransitionException(QueueTokenStatus from, QueueTokenStatus to) {
        super(ErrorCode.INVALID_QUEUE_TOKEN_TRANSITION,
                "Cannot transition queue token from " + from + " to " + to);
    }
}
