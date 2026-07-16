package com.syntagi.appointment.exception;

import com.syntagi.common.exception.ApplicationException;
import com.syntagi.common.exception.ErrorCode;

public class SlotCapacityExceededException extends ApplicationException {

    public SlotCapacityExceededException() {
        super(ErrorCode.SLOT_CAPACITY_EXCEEDED);
    }
}
