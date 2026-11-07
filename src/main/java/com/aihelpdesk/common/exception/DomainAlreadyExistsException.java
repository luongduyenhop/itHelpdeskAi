package com.aihelpdesk.common.exception;

public class DomainAlreadyExistsException extends BusinessException {

    public DomainAlreadyExistsException(String domain) {
        super("DOMAIN_ALREADY_EXISTS", "Domain already exists: " + domain);
    }
}
