package com.syntagi.common.exception;

public class InvalidEntityStateException extends ApplicationException {

    public InvalidEntityStateException(String message) {
        super(ErrorCode.INVALID_ENTITY_STATE, message);
    }
}
