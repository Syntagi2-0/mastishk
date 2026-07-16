package com.syntagi.queue.exception;

import com.syntagi.common.exception.ApplicationException;
import com.syntagi.common.exception.ErrorCode;

public class QueueSessionClosedException extends ApplicationException {

    public QueueSessionClosedException() {
        super(ErrorCode.QUEUE_SESSION_CLOSED);
    }
}
