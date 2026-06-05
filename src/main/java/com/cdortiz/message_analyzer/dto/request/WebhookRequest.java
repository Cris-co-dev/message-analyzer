package com.cdortiz.message_analyzer.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload accepted by the {@code POST /webhook} endpoint.
 *
 * <p>Both fields are validated on arrival at the controller layer; blank
 * values are rejected with HTTP 400 by
 * {@link com.cdortiz.message_analyzer.exception.GlobalExceptionHandler}.
 */
public record WebhookRequest(
        /**
         * Identifier of the user that produced the message. Required and
         * must not be blank — enforced by {@link NotBlank}.
         */
        @NotBlank(message = "user must not be blank")
        String user,

        /**
         * Free-form message text that will be evaluated by
         * {@link com.cdortiz.message_analyzer.service.AlertEvaluatorService}
         * for keyword matches. Required and must not be blank — enforced
         * by {@link NotBlank}.
         */
        @NotBlank(message = "message must not be blank")
        String message) {
}
