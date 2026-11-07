package com.aihelpdesk.common.exception;

public class InvalidUserStatusTransitionException extends BusinessException {

    public InvalidUserStatusTransitionException(String message) {
        super("INVALID_USER_STATUS_TRANSITION", message);
    }
}
