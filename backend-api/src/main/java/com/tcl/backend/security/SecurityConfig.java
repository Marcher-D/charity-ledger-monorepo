package com.tcl.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcl.backend.common.ErrorResponse;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// SecurityConfig creates DaoAuthenticationProvider bean
// -> securityFilterChain registers it with .authenticationProvider(...)
// -> AuthenticationConfiguration knows about it
// -> AuthenticationManager delegates to it

// Marks this class as a Spring configuration class.
// Methods annotated with @Bean create objects managed by Spring.
@Configuration
// Enables Spring Security's web security filter system.
@EnableWebSecurity
// Enables method-level security annotations if we add them later, such as @PreAuthorize.
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    // Defines the main Spring Security HTTP filter chain and route access rules.
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthenticationProvider authenticationProvider,
            CorsConfigurationSource corsConfigurationSource,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        return http
                // Allow configured frontend development origins to call the API from a browser.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // Disable CSRF because this backend uses stateless JWT Authorization headers,
                // not server-side browser sessions/cookies.
                .csrf(csrf -> csrf.disable())
                // Stateless means Spring Security will not create or use an HTTP session
                // to remember logged-in users between requests. Each request must bring JWT again.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Use our database-backed authentication provider for login.
                .authenticationProvider(authenticationProvider)
                // Define which routes are public, admin-only, or require any logged-in user.
                // These requestMatchers store authorization rules during app startup.
                // At request time, Spring applies these rules after filters like JwtAuthenticationFilter run.
                .authorizeHttpRequests(auth -> auth
                        // Register/login endpoints are public because users do not have JWT yet.
                        // permitAll() only applies if this matcher matches; otherwise Spring checks the next rule.
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // The servlet ERROR dispatch (response.sendError -> /error) re-enters this
                        // chain as anonymous. If /error is not permitted, the entry point hijacks the
                        // dispatch and rewrites whatever status the original handler chose.
                        .requestMatchers("/error").permitAll()
                        // Public campaign browsing is allowed without login.
                        // This permits only GET requests. Other campaign methods continue to later rules.
                        .requestMatchers(HttpMethod.GET, "/api/v1/campaigns/**").permitAll()
                        // Admin endpoints require ROLE_ADMIN.
                        // hasRole("ADMIN") only applies to /api/v1/admin/**.
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // Everything else requires a valid authenticated user.
                        .anyRequest().authenticated()
                )
                // Without an explicit entry point, Spring's stateless default answers 403
                // for requests with no/invalid token. 401 is the correct status there:
                // 401 = "you are not authenticated", 403 = "you are authenticated but not allowed".
                // The access-denied handler writes its 403 directly instead of sendError,
                // which would bounce through the /error dispatch.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // Run our JWT filter before Spring's username/password filter.
                // That lets Bearer tokens authenticate requests before route authorization happens.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Build and return the configured SecurityFilterChain bean.
                .build();
    }

    @Bean
    // AuthenticationProvider tells Spring Security how to check login credentials.
    AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        // UserDetailsService loads the account by email.
        provider.setUserDetailsService(userDetailsService);
        // PasswordEncoder checks the raw login password against the stored BCrypt hash.
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    // AuthenticationManager is used by AuthService.login(...) to run Spring Security authentication.
    // It is the standard login entry point: AuthService asks this manager to authenticate,
    // and the manager delegates to the configured provider without AuthService knowing provider details.
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    // BCrypt hashes passwords safely before storing them in accounts.password_hash.
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    // Answers unauthenticated requests with 401 and the same JSON error shape the
    // GlobalExceptionHandler uses, so clients see one consistent error format.
    // (Filter-chain rejections never reach @RestControllerAdvice, hence this bean.)
    AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, exception) ->
                writeError(objectMapper, response, HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    @Bean
    // Answers authenticated-but-not-allowed requests (e.g. USER calling /admin/**)
    // with 403 and the same JSON error shape.
    AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, exception) ->
                writeError(objectMapper, response, HttpStatus.FORBIDDEN, "Access denied");
    }

    // Writes the response body directly; response.sendError would re-dispatch the
    // request to /error and lose control of the status/body.
    private static void writeError(ObjectMapper objectMapper, jakarta.servlet.http.HttpServletResponse response,
                                   HttpStatus status, String message) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, null);
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}") String allowedOrigins
    ) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
