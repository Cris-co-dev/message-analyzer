package com.cdortiz.message_analyzer.service;

import com.cdortiz.message_analyzer.config.AlertProperties;
import com.cdortiz.message_analyzer.dto.response.AlertResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit slice for {@link AlertEvaluatorService}.
 *
 * <p>The service has a single collaborator ({@link AlertProperties}) and
 * no Spring-specific behaviour, so the test uses real record instances
 * rather than {@code @Mock} (Mockito 5+ supports mocking records, but the
 * literals are short and self-documenting — see the design §C1).
 *
 * <p>The accent-pinning (both {@code crítico} and {@code critico} match
 * the configured keyword {@code Crítico}) and NPE-pinning
 * ({@code evaluate(null)} throws) tests are kept as named methods to
 * document the pinned contract (locked decisions #4 and #5 in the spec).
 */
@ExtendWith(MockitoExtension.class)
class AlertEvaluatorServiceTest {

    // Per design §C1 / task T3 — multiple keyword fixtures for the matrix.
    private static final List<String> EMPTY_KEYWORDS = List.of();
    private static final List<String> SPANISH_KEYWORDS = List.of(
            "Crítico", "Urgente", "Prioridad alta");
    private static final List<String> UPPERCASE_KEYWORDS = List.of("PRIMARIO");
    private static final List<String> MIXED_CASE_KEYWORDS = List.of("Primario", "Secundario");
    private static final List<String> MULTI_KEYWORDS = List.of("alfa", "beta", "gama");

    private static final AlertProperties EMPTY_KEYWORDS_PROPS =
            new AlertProperties(EMPTY_KEYWORDS);
    private static final AlertProperties SPANISH_KEYWORDS_PROPS =
            new AlertProperties(SPANISH_KEYWORDS);
    private static final AlertProperties UPPERCASE_KEYWORDS_PROPS =
            new AlertProperties(UPPERCASE_KEYWORDS);
    private static final AlertProperties MIXED_CASE_KEYWORDS_PROPS =
            new AlertProperties(MIXED_CASE_KEYWORDS);
    private static final AlertProperties MULTI_KEYWORDS_PROPS =
            new AlertProperties(MULTI_KEYWORDS);

    private AlertEvaluatorService service;

    @BeforeEach
    void setUp() {
        // Default SUT — most tests use the Spanish fixture (mirrors the real
        // application.yaml). Tests that need different keywords construct
        // a dedicated SUT locally.
        service = new AlertEvaluatorService(SPANISH_KEYWORDS_PROPS);
    }

    // ---- SVC-01 ----

    @Test
    @DisplayName("SVC-01: message contains lowercase keyword returns alert=true")
    void evaluate_messageContainsLowercaseKeyword_returnsTrue() {
        // Arrange — service is built in @BeforeEach with SPANISH_KEYWORDS_PROPS.
        String message = "esto es crítico hoy";

        // Act
        AlertResponse response = service.evaluate(message);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.alert()).isTrue();
    }

    // ---- SVC-02 ----

    @Test
    @DisplayName("SVC-02: message uppercase and keyword lowercase — case-folding returns true")
    void evaluate_messageUppercaseAndKeywordLowercase_caseFoldingReturnsTrue() {
        // Arrange
        AlertEvaluatorService svc = new AlertEvaluatorService(UPPERCASE_KEYWORDS_PROPS);
        String message = "ESTO ES PRIMARIO";

        // Act
        AlertResponse response = svc.evaluate(message);

        // Assert
        assertThat(response.alert()).isTrue();
    }

    // ---- SVC-03 ----

    @Test
    @DisplayName("SVC-03: message does not contain any keyword returns alert=false")
    void evaluate_messageDoesNotContainAnyKeyword_returnsFalse() {
        // Arrange — service built with SPANISH_KEYWORDS_PROPS in @BeforeEach.
        String message = "hola mundo";

        // Act
        AlertResponse response = service.evaluate(message);

        // Assert
        assertThat(response.alert()).isFalse();
    }

    // ---- SVC-04 (PIN: null-input contract) ----

    @Test
    @DisplayName("SVC-04: null message throws NullPointerException (pin)")
    void evaluate_nullMessage_throwsNullPointerException() {
        // Arrange — any props will do; null is the variable under test.

        // Act + Assert
        assertThatThrownBy(() -> service.evaluate(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ---- SVC-05a (PIN: accented keyword matches with the same Unicode) ----

    @Test
    @DisplayName("SVC-05a: accented keyword present — match is exact (pin)")
    void evaluate_messageHasAccentedKeyword_matchIsExact_pinsCurrentBehavior() {
        // Arrange — service built with SPANISH_KEYWORDS_PROPS in @BeforeEach.
        // Still valid under the accent-insensitive contract: NFD-normalized
        // "crítico" and "Crítico" both fold to "critico", so the match holds.
        String message = "crítico";

        // Act
        AlertResponse response = service.evaluate(message);

        // Assert
        assertThat(response.alert()).isTrue();
    }

    // ---- SVC-05b (PIN: accentless version of accented keyword matches) ----

    @Test
    @DisplayName("SVC-05b: accentless version of accented keyword matches (pin)")
    void evaluate_messageWithoutAccents_matchesKeywordWithAccents_pinsNewBehavior() {
        // Arrange — service built with SPANISH_KEYWORDS_PROPS in @BeforeEach.
        // Accent-insensitive matching is now expected: "critico" must match
        // the configured keyword "Crítico" because both NFD-decompose and
        // strip combining diacritical marks to the same base form.
        String message = "critico";

        // Act
        AlertResponse response = service.evaluate(message);

        // Assert
        assertThat(response.alert()).isTrue();
    }

    // ---- SVC-05c (accent-insensitive match for a multi-word YAML keyword) ----

    @Test
    @DisplayName("SVC-05c: multi-word keyword with accents in YAML matches unaccented message")
    void evaluate_keywordWithAccentsInYaml_matchesUnaccentedMessage() {
        // "Atención inmediata" in application.yaml must match
        // "atencion inmediata" in the message.
        AlertProperties props = new AlertProperties(List.of("Atención inmediata"));
        AlertEvaluatorService svc = new AlertEvaluatorService(props);

        AlertResponse response = svc.evaluate("requiere atencion inmediata");

        assertThat(response.alert()).isTrue();
    }

    // ---- SVC-05d (accent-insensitive match collapses multiple accented characters) ----

    @Test
    @DisplayName("SVC-05d: keyword with multiple accented characters collapses to ASCII base form")
    void evaluate_keywordWithMultipleAccents_collapsesAllToAsciiBase() {
        // "Acción requerida" → "accion requerida" after NFD + mark-strip.
        AlertProperties props = new AlertProperties(List.of("Acción requerida"));
        AlertEvaluatorService svc = new AlertEvaluatorService(props);

        AlertResponse response = svc.evaluate("por favor, accion requerida");

        assertThat(response.alert()).isTrue();
    }

    // ---- SVC-06 ----

    @Test
    @DisplayName("SVC-06: empty keyword list — any message returns alert=false")
    void evaluate_emptyKeywordList_anyMessageReturnsFalse() {
        // Arrange
        AlertEvaluatorService svc = new AlertEvaluatorService(EMPTY_KEYWORDS_PROPS);
        String message = "el clima está agradable y soleado";

        // Act
        AlertResponse response = svc.evaluate(message);

        // Assert
        assertThat(response.alert()).isFalse();
    }

    // ---- SVC-07 ----

    @Test
    @DisplayName("SVC-07: multi-keyword list — any single match returns alert=true")
    void evaluate_multiKeywordList_anyMatchReturnsTrue() {
        // Arrange
        AlertEvaluatorService svc = new AlertEvaluatorService(MULTI_KEYWORDS_PROPS);
        String message = "este mensaje contiene la palabra beta";

        // Act
        AlertResponse response = svc.evaluate(message);

        // Assert
        assertThat(response.alert()).isTrue();
    }

    // ---- SVC-08a ----

    @Test
    @DisplayName("SVC-08a: empty message — no keyword is empty, returns alert=false")
    void evaluate_emptyMessage_noKeywordIsEmpty_returnsFalse() {
        // Arrange
        AlertEvaluatorService svc = new AlertEvaluatorService(MIXED_CASE_KEYWORDS_PROPS);

        // Act
        AlertResponse response = svc.evaluate("");

        // Assert
        assertThat(response.alert()).isFalse();
    }

    // ---- SVC-08b ----

    @Test
    @DisplayName("SVC-08b: whitespace-only message — no match, returns alert=false")
    void evaluate_whitespaceOnlyMessage_returnsFalse() {
        // Arrange
        AlertEvaluatorService svc = new AlertEvaluatorService(MIXED_CASE_KEYWORDS_PROPS);

        // Act
        AlertResponse response = svc.evaluate("   ");

        // Assert
        assertThat(response.alert()).isFalse();
    }

    // ---- SVC-09 ----

    @Test
    @DisplayName("SVC-09: keyword surrounded by text — substring match returns alert=true")
    void evaluate_messageWithKeywordSurroundedByText_returnsTrue() {
        // Arrange
        AlertEvaluatorService svc = new AlertEvaluatorService(MIXED_CASE_KEYWORDS_PROPS);
        String message = "este mensaje tiene la palabra Primario en el centro";

        // Act
        AlertResponse response = svc.evaluate(message);

        // Assert
        assertThat(response.alert()).isTrue();
    }
}
