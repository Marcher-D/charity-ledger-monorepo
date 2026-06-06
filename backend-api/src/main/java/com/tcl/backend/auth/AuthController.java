package com.tcl.backend.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// REST controller for authentication endpoints.
// It handles HTTP requests, validates JSON bodies, and delegates logic to AuthService.
@RestController
// Every route in this controller starts with /api/v1/auth.
@RequestMapping("/api/v1/auth")
public class AuthController {

    // Service that contains register/login business logic.
    private final AuthService authService;

    // Constructor injection: Spring provides the AuthService bean.
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // Handles POST /api/v1/auth/register.
    @PostMapping("/register")
    // Successful registration should return HTTP 201 Created instead of default 200 OK.
    @ResponseStatus(HttpStatus.CREATED)
    // @RequestBody converts JSON body into RegisterRequest.
    // @Valid runs validation annotations like @NotBlank, @Email, and @Size.
    AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    // Handles POST /api/v1/auth/login.
    @PostMapping("/login")
    // Login returns default HTTP 200 OK when successful.
    // @RequestBody converts JSON body into LoginRequest, and @Valid checks its fields.
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
