package com.aihelpdesk.common.exception;

public class InvalidEmailDomainException extends BusinessException {

    public InvalidEmailDomainException(String message) {
        super("INVALID_EMAIL_DOMAIN", message);
    }
}
