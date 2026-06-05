package com.cdortiz.message_analyzer.dto.response;

/**
 * Response returned by the {@code POST /webhook} endpoint summarising the
 * outcome of an alert evaluation.
 */
public record AlertResponse(
        /**
         * Verdict of the evaluation: {@code true} when the supplied
         * message contains at least one configured keyword, otherwise
         * {@code false}. Never {@code null}.
         */
        boolean alert) {}
