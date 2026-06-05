package com.cdortiz.message_analyzer.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean Validation slice for {@link WebhookRequest}.
 *
 * <p>Exercises the {@code @NotBlank} constraints on the record's
 * {@code user} and {@code message} fields directly through a JSR-380
 * {@link Validator}, without spinning up a Spring context. The
 * {@code Validator} is constructed once at class load and reused across
 * all tests (the JSR-380 spec guarantees thread-safety).
 */
class WebhookRequestValidationTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    // ---- VAL-01 ----

    @Test
    @DisplayName("VAL-01: valid request produces zero violations")
    void validRequest_producesZeroViolations() {
        // Arrange
        WebhookRequest request = new WebhookRequest("Juan Pérez", "Hola, ¿cómo están?");

        // Act
        Set<ConstraintViolation<WebhookRequest>> violations = VALIDATOR.validate(request);

        // Assert
        assertThat(violations).isEmpty();
    }

    // ---- VAL-02 ----

    @Test
    @DisplayName("VAL-02: blank user produces one violation on the user property")
    void blankUser_producesViolationOnUserProperty() {
        // Arrange
        WebhookRequest request = new WebhookRequest("", "Hola, ¿cómo están?");

        // Act
        Set<ConstraintViolation<WebhookRequest>> violations = VALIDATOR.validate(request);

        // Assert
        assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("user");
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("user must not be blank");
    }

    // ---- VAL-03 ----

    @Test
    @DisplayName("VAL-03: blank message produces one violation on the message property")
    void blankMessage_producesViolationOnMessageProperty() {
        // Arrange
        WebhookRequest request = new WebhookRequest("Juan Pérez", "");

        // Act
        Set<ConstraintViolation<WebhookRequest>> violations = VALIDATOR.validate(request);

        // Assert
        assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("message");
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("message must not be blank");
    }

    // ---- VAL-04 ----

    @Test
    @DisplayName("VAL-04: null user AND null message produces exactly two violations")
    void nullUserAndNullMessage_producesTwoViolations() {
        // Arrange
        WebhookRequest request = new WebhookRequest(null, null);

        // Act
        Set<ConstraintViolation<WebhookRequest>> violations = VALIDATOR.validate(request);

        // Assert
        assertThat(violations)
                .hasSize(2)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("user", "message");
    }

    // ---- VAL-02 (boundary: whitespace-only) ----

    @Test
    @DisplayName("VAL-02 boundary: whitespace-only user produces one violation on the user property")
    void whitespaceOnlyUser_producesViolationOnUserProperty() {
        // Arrange
        WebhookRequest request = new WebhookRequest("   ", "Hola, ¿cómo están?");

        // Act
        Set<ConstraintViolation<WebhookRequest>> violations = VALIDATOR.validate(request);

        // Assert
        assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("user");
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("user must not be blank");
    }

    // ---- VAL-03 (boundary: whitespace-only) ----

    @Test
    @DisplayName("VAL-03 boundary: whitespace-only message produces one violation on the message property")
    void whitespaceOnlyMessage_producesViolationOnMessageProperty() {
        // Arrange
        WebhookRequest request = new WebhookRequest("Juan Pérez", "   ");

        // Act
        Set<ConstraintViolation<WebhookRequest>> violations = VALIDATOR.validate(request);

        // Assert
        assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("message");
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("message must not be blank");
    }
}
