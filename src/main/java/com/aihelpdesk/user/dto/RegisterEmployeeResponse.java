package com.aihelpdesk.user.dto;

public record RegisterEmployeeResponse(
    String message
) {

    public static final String NEUTRAL_MESSAGE =
        "Registration request received. Accounts meeting eligibility criteria will be reviewed by an administrator.";

    public RegisterEmployeeResponse() {
        this(NEUTRAL_MESSAGE);
    }

    public static RegisterEmployeeResponse defaultResponse() {
        return new RegisterEmployeeResponse(NEUTRAL_MESSAGE);
    }
}
