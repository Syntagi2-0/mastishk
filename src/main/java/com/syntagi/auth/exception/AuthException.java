package com.syntagi.auth.exception;

import com.syntagi.common.exception.ApplicationException;
import com.syntagi.common.exception.ErrorCode;

public class AuthException extends ApplicationException {

    public AuthException(ErrorCode errorCode) {
        super(errorCode);
    }
}
