package com.aihelpdesk.common.exception;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException(Long userId) {
        super("USER_NOT_FOUND", "User not found with id: " + userId);
    }

    public UserNotFoundException(String email) {
        super("USER_NOT_FOUND", "User not found with email: " + email);
    }
}
