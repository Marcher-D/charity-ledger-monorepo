package com.tcl.backend.auth;

// Response DTO returned after successful register/login.
// No validation annotations are needed because the backend creates this object itself.
public record AuthResponse(
        // JWT string the frontend sends later in Authorization: Bearer <token>.
        String accessToken,
        // Token type is usually "Bearer".
        String tokenType,
        // Number of seconds before accessToken expires.
        // The JWT already contains the real exp claim; this is convenience metadata for frontend.
        long expiresIn
) {
}
