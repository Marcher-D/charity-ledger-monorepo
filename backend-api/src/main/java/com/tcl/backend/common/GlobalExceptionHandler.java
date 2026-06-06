package com.tcl.backend.common;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/*
 * Central place for converting Java exceptions into HTTP JSON error responses.
 *
 * Without this class, each controller would need to manually catch errors and build
 * ResponseEntity objects. With @RestControllerAdvice, Spring applies this handler
 * globally to all REST controllers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /*
     * Handles expected business/API errors thrown by our own services.
     *
     * Example:
     * throw new ApiException(HttpStatus.NOT_FOUND, "Campaign not found");
     *
     * This method preserves the exact status and message chosen by the service.
     */
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
        return error(exception.getStatus(), exception.getMessage(), null);
    }

    /*
     * Handles request body validation errors from @Valid.
     *
     * Example invalid request:
     * {
     *   "email": "not-an-email",
     *   "password": "123"
     * }
     *
     * Spring detects field errors before the controller/service logic runs.
     * We collect them into a map so the frontend can show messages next to fields.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> validation = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> validation.put(error.getField(), error.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "Validation failed", validation);
    }

    /*
     * Handles authorization failures.
     *
     * Example:
     * a USER account tries to call /api/v1/admin/campaigns/{id}/approve.
     *
     * Authentication answers "who are you?"
     * Authorization answers "are you allowed to do this?"
     * This method is for authorization failures.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        return error(HttpStatus.FORBIDDEN, "Access denied", null);
    }

    /*
     * Handles authentication failures from Spring Security login checks.
     *
     * Example:
     * AuthService.login(...) calls AuthenticationManager with a wrong password,
     * and Spring throws BadCredentialsException, which extends AuthenticationException.
     */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException exception) {
        return error(HttpStatus.UNAUTHORIZED, "Invalid email or password", null);
    }

    /*
     * Last safety net for unexpected errors.
     *
     * We do not expose exception details to the client because stack traces can leak
     * implementation/security details. Logs should be used for debugging instead.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", null);
    }

    /*
     * Shared helper to build a consistent error response body.
     *
     * ResponseEntity controls the real HTTP status code.
     * ErrorResponse is the JSON body returned to the frontend.
     */
    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message, Map<String, String> validation) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                validation
        );
        return ResponseEntity.status(status).body(response);
    }
}
