package com.aihelpdesk.common.exception;

public class InvalidDomainNameException extends BusinessException {

    public InvalidDomainNameException(String message) {
        super("INVALID_DOMAIN_NAME", message);
    }
}
