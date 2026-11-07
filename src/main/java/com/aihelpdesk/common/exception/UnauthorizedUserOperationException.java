package com.aihelpdesk.common.exception;

public class UnauthorizedUserOperationException extends BusinessException {

    public UnauthorizedUserOperationException(String message) {
        super("UNAUTHORIZED_USER_OPERATION", message);
    }
}
