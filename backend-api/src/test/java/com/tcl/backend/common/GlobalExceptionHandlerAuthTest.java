package com.tcl.backend.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerAuthTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ThrowingAuthController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void badCredentialsReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/login"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @RestController
    private static class ThrowingAuthController {

        @PostMapping("/login")
        void login() {
            throw new BadCredentialsException("Bad credentials");
        }
    }
}
