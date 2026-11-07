package com.aihelpdesk.common.exception;

public class EmailAlreadyExistsException extends BusinessException {

    public EmailAlreadyExistsException(String email) {
        super("EMAIL_ALREADY_EXISTS", "Email already exists: " + email);
    }
}
