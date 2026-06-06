package com.tcl.backend.users;

import com.tcl.backend.security.AuthenticatedAccount;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// @RestController tells Spring this class handles HTTP API requests.
// Return values are automatically converted to JSON responses.
@RestController
// Every endpoint in this controller starts with /api/v1/users.
@RequestMapping("/api/v1/users")
public class UserController {

    // Business logic lives in the service, not directly in the controller.
    private final UserService userService;

    // Constructor injection: Spring provides the UserService bean.
    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Handles GET /api/v1/users/me.
    @GetMapping("/me")
    // @AuthenticationPrincipal asks Spring Security for the currently logged-in account.
    UserProfileResponse me(@AuthenticationPrincipal AuthenticatedAccount principal) {
        // Delegate to UserService, then return the response object as JSON.
        return userService.me(principal);
    }
}
