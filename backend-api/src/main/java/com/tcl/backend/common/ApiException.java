package com.tcl.backend.common;

import org.springframework.http.HttpStatus;

/*
 * Custom exception for expected API/business errors.
 *
 * It extends RuntimeException so service methods can throw it without declaring
 * "throws ApiException" on every method signature. Spring will still catch it in
 * GlobalExceptionHandler because that class has @ExceptionHandler(ApiException.class).
 *
 * Example:
 * throw new ApiException(HttpStatus.NOT_FOUND, "Campaign not found");
 */
public class ApiException extends RuntimeException {

    /*
     * HTTP status that should be returned to the client.
     *
     * Examples:
     * - 400 BAD_REQUEST
     * - 404 NOT_FOUND
     * - 409 CONFLICT
     */
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        // Store the message inside RuntimeException so getMessage() works.
        super(message);
        this.status = status;
    }

    // Used by GlobalExceptionHandler to build the HTTP response status.
    public HttpStatus getStatus() {
        return status;
    }
}
