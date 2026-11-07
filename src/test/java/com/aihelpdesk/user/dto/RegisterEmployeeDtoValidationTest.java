package com.aihelpdesk.user.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterEmployeeDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void shouldPassValidationWhenEmailIsValid() {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest("employee@example.com", "Password123!", "Nguyen Van A");
        Set<ConstraintViolation<RegisterEmployeeRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldFailValidationWhenEmailIsBlank() {
        RegisterEmployeeRequest empty = new RegisterEmployeeRequest("", "Password123!", "Nguyen Van A");
        RegisterEmployeeRequest blank = new RegisterEmployeeRequest("   ", "Password123!", "Nguyen Van A");
        RegisterEmployeeRequest nullEmail = new RegisterEmployeeRequest(null, "Password123!", "Nguyen Van A");

        assertThat(validator.validate(empty)).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
        assertThat(validator.validate(blank)).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
        assertThat(validator.validate(nullEmail)).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    void shouldFailValidationWhenEmailIsInvalid() {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest("not-an-email", "Password123!", "Nguyen Van A");
        Set<ConstraintViolation<RegisterEmployeeRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("email");
    }

    @Test
    void shouldFailValidationWhenEmailExceeds320Characters() {
        String longEmail = "a".repeat(310) + "@example.com";
        assertThat(longEmail.length()).isGreaterThan(320);
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(longEmail, "Password123!", "Nguyen Van A");
        Set<ConstraintViolation<RegisterEmployeeRequest>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    void shouldPassValidationWhenPasswordIsExactly8Characters() {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest("employee@example.com", "12345678", "Nguyen Van A");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shouldFailValidationWhenPasswordHasFewerThan8Characters() {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest("employee@example.com", "1234567", "Nguyen Van A");
        Set<ConstraintViolation<RegisterEmployeeRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("password");
    }

    @Test
    void shouldPassValidationWhenPasswordIsExactly72Utf8Bytes() {
        String pass72 = "a".repeat(72);
        RegisterEmployeeRequest request = new RegisterEmployeeRequest("employee@example.com", pass72, "Nguyen Van A");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shouldFailValidationWhenPasswordExceeds72Utf8Bytes() {
        String pass73 = "a".repeat(73);
        RegisterEmployeeRequest request = new RegisterEmployeeRequest("employee@example.com", pass73, "Nguyen Van A");
        Set<ConstraintViolation<RegisterEmployeeRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("password");
    }

    @Test
    void shouldValidateMultibyteVietnameseCharacters() {
        RegisterEmployeeRequest validVietnamese = new RegisterEmployeeRequest("employee@example.com", "MậtKhẩu1", "Nguyen Van A");
        assertThat(validator.validate(validVietnamese)).isEmpty();

        String exact72Bytes = "ệ".repeat(24);
        RegisterEmployeeRequest valid72Bytes = new RegisterEmployeeRequest("employee@example.com", exact72Bytes, "Nguyen Van A");
        assertThat(validator.validate(valid72Bytes)).isEmpty();

        RegisterEmployeeRequest shortVietnamese = new RegisterEmployeeRequest("employee@example.com", "MậtKhẩu", "Nguyen Van A");
        assertThat(validator.validate(shortVietnamese)).anyMatch(v -> v.getPropertyPath().toString().equals("password"));

        String exceeds72Bytes = "ệ".repeat(25);
        RegisterEmployeeRequest invalid75Bytes = new RegisterEmployeeRequest("employee@example.com", exceeds72Bytes, "Nguyen Van A");
        assertThat(validator.validate(invalid75Bytes)).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    void shouldValidateEmojiMultibyteBoundary() {
        String eightEmojis = "😀".repeat(8);
        RegisterEmployeeRequest validEight = new RegisterEmployeeRequest("employee@example.com", eightEmojis, "Nguyen Van A");
        assertThat(validator.validate(validEight)).isEmpty();

        String eighteenEmojis = "😀".repeat(18);
        RegisterEmployeeRequest valid18 = new RegisterEmployeeRequest("employee@example.com", eighteenEmojis, "Nguyen Van A");
        assertThat(validator.validate(valid18)).isEmpty();

        String sevenEmojis = "😀".repeat(7);
        RegisterEmployeeRequest shortEmoji = new RegisterEmployeeRequest("employee@example.com", sevenEmojis, "Nguyen Van A");
        assertThat(validator.validate(shortEmoji)).anyMatch(v -> v.getPropertyPath().toString().equals("password"));

        String nineteenEmojis = "😀".repeat(19);
        RegisterEmployeeRequest exceedsEmoji = new RegisterEmployeeRequest("employee@example.com", nineteenEmojis, "Nguyen Van A");
        assertThat(validator.validate(exceedsEmoji)).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    void shouldFailValidationWhenFullNameIsBlank() {
        RegisterEmployeeRequest empty = new RegisterEmployeeRequest("employee@example.com", "Password123!", "");
        RegisterEmployeeRequest blank = new RegisterEmployeeRequest("employee@example.com", "Password123!", "   ");
        RegisterEmployeeRequest nullName = new RegisterEmployeeRequest("employee@example.com", "Password123!", null);

        assertThat(validator.validate(empty)).anyMatch(v -> v.getPropertyPath().toString().equals("fullName"));
        assertThat(validator.validate(blank)).anyMatch(v -> v.getPropertyPath().toString().equals("fullName"));
        assertThat(validator.validate(nullName)).anyMatch(v -> v.getPropertyPath().toString().equals("fullName"));
    }

    @Test
    void shouldFailValidationWhenFullNameIsShorterThan2Characters() {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest("employee@example.com", "Password123!", "A");
        Set<ConstraintViolation<RegisterEmployeeRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("fullName");
    }

    @Test
    void shouldFailValidationWhenFullNameIsLongerThan150Characters() {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest("employee@example.com", "Password123!", "A".repeat(151));
        Set<ConstraintViolation<RegisterEmployeeRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("fullName");
    }

    @Test
    void shouldRedactRawPasswordInToString() {
        String rawPassword = "SensitiveSecretPassword999!";
        RegisterEmployeeRequest request = new RegisterEmployeeRequest("employee@example.com", rawPassword, "Nguyen Van A");
        String stringRep = request.toString();
        assertThat(stringRep).doesNotContain(rawPassword);
        assertThat(stringRep).contains("[PROTECTED]");
        assertThat(stringRep).contains("employee@example.com");
        assertThat(stringRep).contains("Nguyen Van A");
    }

    @Test
    void shouldNotExposeAccountSpecificFieldsInRegisterEmployeeResponse() {
        List<Field> instanceFields = Arrays.stream(RegisterEmployeeResponse.class.getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .toList();
        assertThat(instanceFields).hasSize(1);
        assertThat(instanceFields.getFirst().getName()).isEqualTo("message");

        RegisterEmployeeResponse response = RegisterEmployeeResponse.defaultResponse();
        assertThat(response.message()).isEqualTo(RegisterEmployeeResponse.NEUTRAL_MESSAGE);

        List<String> prohibitedNames = List.of(
            "id", "userId", "email", "role", "status", "approvedBy", "approvedAt", "disabledAt", "password", "passwordHash"
        );
        for (Field field : RegisterEmployeeResponse.class.getDeclaredFields()) {
            assertThat(prohibitedNames).doesNotContain(field.getName());
        }
    }
}
