package com.tcl.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Request DTO for POST /api/v1/auth/register.
// A record is a compact Java class for immutable data.
public record RegisterRequest(
        // username must exist, must not be only spaces, and must be at most 100 characters.
        @NotBlank @Size(max = 100) String username,
        // email must exist, look like an email address, and fit the database column size.
        @NotBlank @Email @Size(max = 255) String email,
        // password must exist and be 8-100 characters before it is hashed by BCrypt.
        @NotBlank @Size(min = 8, max = 100) String password
) {
}
