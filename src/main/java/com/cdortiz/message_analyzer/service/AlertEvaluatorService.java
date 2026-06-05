package com.cdortiz.message_analyzer.service;

import com.cdortiz.message_analyzer.config.AlertProperties;
import com.cdortiz.message_analyzer.dto.response.AlertResponse;
import org.springframework.stereotype.Service;

import java.text.Normalizer;

/**
 * Evaluates a free-form message against the configured keyword list and
 * decides whether it constitutes an alert.
 *
 * <p>The evaluation is case-insensitive AND accent-insensitive: the input
 * message and the configured keywords are both lower-cased and stripped
 * of combining diacritical marks (Unicode NFD normalization) before
 * comparison. This means "crítico", "critico", and "CRÍTICO" all match
 * the configured keyword "Crítico". Locale-sensitive folding (e.g.
 * Turkish dotless-i) is not applied and remains a known limitation.
 *
 * <p>The service is stateless and has no side effects, so it runs
 * outside any transaction boundary and is safe to invoke from any thread.
 */
@Service
public class AlertEvaluatorService {

    private final AlertProperties properties;

    public AlertEvaluatorService(AlertProperties properties) {
        this.properties = properties;
    }

    /**
     * Determines whether the supplied message contains any keyword
     * configured in {@link AlertProperties#keywords()}.
     *
     * @param message the free-form text to evaluate; must not be {@code null}
     * @return an {@link AlertResponse} with {@code alert == true} when at
     *         least one configured keyword is found in the message,
     *         otherwise {@code alert == false}
     */
    public AlertResponse evaluate(String message) {

        String normalized = normalizeForComparison(message);

        boolean alert = properties.keywords()
                .stream()
                .map(AlertEvaluatorService::normalizeForComparison)
                .anyMatch(normalized::contains);

        return new AlertResponse(alert);
    }

    /**
     * Lower-cases the input AND strips combining diacritical marks via
     * Unicode NFD decomposition, so accent-bearing characters collapse to
     * their ASCII base form (e.g. "Crítico" → "critico", "ñ" → "n").
     *
     * <p>Used to make keyword matching accent-insensitive: a message typed
     * without the correct diacritics ("critico") will still match a
     * configured keyword that has them ("Crítico").
     */
    private static String normalizeForComparison(String value) {
        return Normalizer.normalize(value.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}", "");
    }
}
