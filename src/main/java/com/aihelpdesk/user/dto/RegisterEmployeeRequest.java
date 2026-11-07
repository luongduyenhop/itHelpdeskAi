package com.aihelpdesk.user.dto;

import com.aihelpdesk.user.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterEmployeeRequest(
    @NotBlank
    @Email
    @Size(max = 320)
    String email,

    @NotBlank
    @ValidPassword
    String password,

    @NotBlank
    @Size(min = 2, max = 150)
    String fullName
) {

    @Override
    public String toString() {
        return "RegisterEmployeeRequest[email=" + email + ", password=[PROTECTED], fullName=" + fullName + "]";
    }
}
