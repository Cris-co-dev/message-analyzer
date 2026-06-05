package com.cdortiz.message_analyzer.exception;


import com.cdortiz.message_analyzer.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Centralised exception-to-HTTP-response mapping for the REST API.
 *
 * <p>Every handler returns an {@link ErrorResponse} envelope so that
 * clients receive a consistent error shape regardless of where the
 * failure originated. Unexpected exceptions are converted to HTTP 500
 * with a generic message — internal details are not exposed on the
 * wire.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles validation failures triggered by {@code @Valid} on
     * {@code @RequestBody} arguments, surfacing the first field-level
     * error message from the binding result.
     *
     * @param ex the binding exception produced by Spring's argument
     *           resolution, containing the offending field errors
     * @return HTTP {@code 400 Bad Request} with an {@link ErrorResponse}
     *         whose {@code message} is the binding result's default
     *         message for the first field error
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {

        String message = Objects.requireNonNull(ex.getBindingResult()
                        .getFieldError())
                .getDefaultMessage();

        ErrorResponse response = new ErrorResponse(
                "Bad Request",
                message,
                LocalDateTime.now()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    /**
     * Handles requests whose body cannot be deserialised — typically
     * malformed JSON, missing body, or type mismatches.
     *
     * @param ex the conversion exception raised by Spring's
     *           {@code HttpMessageConverter}
     * @return HTTP {@code 400 Bad Request} with an {@link ErrorResponse}
     *         whose {@code message} is the static string
     *         {@code "Malformed JSON request"}
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(
            HttpMessageNotReadableException ex) {

        ErrorResponse response = new ErrorResponse(
                "Bad Request",
                "Malformed JSON request",
                LocalDateTime.now()
        );

        return ResponseEntity
                .badRequest()
                .body(response);
    }

    /**
     * Handles requests whose HTTP method is not supported by the target
     * endpoint (e.g. {@code GET /webhook} when only {@code POST} is
     * mapped). Surfaced by Spring's
     * {@code RequestMappingHandlerMapping} before any controller is
     * invoked.
     *
     * @param ex the method-not-supported exception, carrying the
     *           offending method and the set of supported methods
     * @return HTTP {@code 405 Method Not Allowed} with an
     *         {@link ErrorResponse} whose {@code error} label is
     *         {@code "Method Not Allowed"}
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {

        ErrorResponse response = new ErrorResponse(
                "Method Not Allowed",
                "HTTP method not allowed for this endpoint",
                LocalDateTime.now()
        );

        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(response);
    }

    /**
     * Handles requests that do not match any registered handler
     * (e.g. {@code POST /webhook/nonexistent}). Spring 6.1+ raises
     * {@link NoResourceFoundException} from the static-resource
     * fallback path; the older {@link NoHandlerFoundException} is
     * covered as well for completeness when
     * {@code spring.mvc.throw-exception-if-no-handler-found} is
     * enabled.
     *
     * @param ex the unhandled-request exception
     * @return HTTP {@code 404 Not Found} with an {@link ErrorResponse}
     *         whose {@code error} label is {@code "Not Found"}
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception ex) {

        ErrorResponse response = new ErrorResponse(
                "Not Found",
                "Resource not found",
                LocalDateTime.now()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    /**
     * Catch-all handler for any uncaught exception thrown by a
     * controller, ensuring a uniform error envelope is returned to the
     * client instead of leaking framework defaults.
     *
     * @param ex the unhandled exception
     * @return HTTP {@code 500 Internal Server Error} with an
     *         {@link ErrorResponse} whose {@code message} is the static
     *         string {@code "Unexpected error occurred"}
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex) {

        ErrorResponse response = new ErrorResponse(
                "Internal Server Error",
                "Unexpected error occurred",
                LocalDateTime.now()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}
