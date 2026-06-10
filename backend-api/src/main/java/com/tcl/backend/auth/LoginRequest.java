package com.tcl.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// Request DTO for POST /api/v1/auth/login.
// public means other classes can use it.
// record means this is a compact immutable data object for JSON input.
public record LoginRequest(
        // email must exist and look like an email address.
        @NotBlank @Email String email,
        // password must exist. Spring Security will compare it with the stored BCrypt hash.
        @NotBlank String password
) {
}
