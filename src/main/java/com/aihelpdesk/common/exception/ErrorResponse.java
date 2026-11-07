package com.aihelpdesk.common.exception;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
    String errorCode,
    String message,
    LocalDateTime timestamp,
    List<String> details
) {

    public ErrorResponse(String errorCode, String message) {
        this(errorCode, message, LocalDateTime.now(), null);
    }

    public ErrorResponse(String errorCode, String message, List<String> details) {
        this(errorCode, message, LocalDateTime.now(), details);
    }
}
