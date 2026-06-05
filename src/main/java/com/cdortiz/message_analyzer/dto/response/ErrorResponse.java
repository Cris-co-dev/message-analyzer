package com.cdortiz.message_analyzer.dto.response;

import java.time.LocalDateTime;

/**
 * Standard error envelope returned by
 * {@link com.cdortiz.message_analyzer.exception.GlobalExceptionHandler}
 * for any validation, transport, or unexpected failure in the REST API.
 *
 * <p>All fields are always populated by the handler; none are
 * {@code null} on the wire.
 */
public record ErrorResponse(
        /**
         * Short, human-readable label of the error category
         * (e.g. {@code "Bad Request"}, {@code "Internal Server Error"}).
         * Never {@code null}.
         */
        String error,

        /**
         * Detailed message describing the specific failure — either the
         * validation message from the binding result or a generic
         * descriptor for transport / unexpected errors. Never
         * {@code null}.
         */
        String message,

        /**
         * Server-side timestamp captured at the moment the exception was
         * handled. Never {@code null}.
         */
        LocalDateTime timestamp
) {
}
