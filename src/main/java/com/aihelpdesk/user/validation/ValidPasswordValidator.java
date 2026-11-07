package com.aihelpdesk.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class ValidPasswordValidator implements ConstraintValidator<ValidPassword, String> {

    private static final int MIN_CHARACTERS = 8;
    private static final int MAX_UTF8_BYTES = 72;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount < MIN_CHARACTERS) {
            return false;
        }
        int utf8ByteCount = value.getBytes(StandardCharsets.UTF_8).length;
        return utf8ByteCount <= MAX_UTF8_BYTES;
    }
}
