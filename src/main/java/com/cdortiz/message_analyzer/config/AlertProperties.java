package com.cdortiz.message_analyzer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Strongly-typed binding for the {@code alert.*} namespace in
 * {@code application.yml} / {@code application.properties}.
 *
 * <p>Discovered by
 * {@link org.springframework.boot.context.properties.ConfigurationPropertiesScan}
 * on {@link com.cdortiz.message_analyzer.MessageAnalyzerApplication} and
 * consumed by
 * {@link com.cdortiz.message_analyzer.service.AlertEvaluatorService} to
 * decide whether an incoming message should raise an alert.
 *
 * <p>Example configuration:
 * <pre>
 * alert:
 *   keywords:
 *     - urgent
 *     - critical
 * </pre>
 */
@ConfigurationProperties("alert")
public record AlertProperties(
        /**
         * List of keywords that, when found (case-insensitively) inside
         * an incoming message, cause
         * {@link com.cdortiz.message_analyzer.service.AlertEvaluatorService}
         * to flag the message as an alert. The list is required — an
         * empty list means no message will ever trigger an alert.
         */
        List<String> keywords
) {}
