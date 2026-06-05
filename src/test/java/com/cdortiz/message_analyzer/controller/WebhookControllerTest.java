package com.cdortiz.message_analyzer.controller;

import com.cdortiz.message_analyzer.service.AlertEvaluatorService;
import com.cdortiz.message_analyzer.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice for {@link WebhookController}.
 *
 * <p>Uses {@code @WebMvcTest(WebhookController.class)} so only the web
 * layer is loaded. The {@code @RestControllerAdvice} on
 * {@code GlobalExceptionHandler} is auto-detected, which means the 400
 * and 500 envelopes are exercised end-to-end through real HTTP traffic
 * (locked decision #3 in the spec).
 *
 * <p>The {@link AlertEvaluatorService} is replaced by a {@link MockitoBean}
 * (Spring Boot 3.5.14's replacement for the deprecated {@code @MockBean})
 * so we can control its return value / exception without spinning up a
 * full Spring context.
 */
@WebMvcTest(WebhookController.class)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlertEvaluatorService alertEvaluatorService;

    @Autowired
    private ObjectMapper objectMapper;

    // ---- CTRL-01a ----

    @Test
    @DisplayName("CTRL-01a: valid request with alert=true returns 200 and alert=true")
    void post_validRequest_returns200WithAlertTrue() throws Exception {
        // Arrange
        given(alertEvaluatorService.evaluate(anyString()))
                .willReturn(TestFixtures.alertTrueResponse());

        // Act + Assert
        mockMvc.perform(post("/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestFixtures.webhookRequestJson(objectMapper, "Desconocido", "hay un error")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.alert").value(true));
    }

    // ---- CTRL-01b ----

    @Test
    @DisplayName("CTRL-01b: valid request with alert=false returns 200 and alert=false")
    void post_validRequestWithAlertFalse_returns200WithAlertFalse() throws Exception {
        // Arrange
        given(alertEvaluatorService.evaluate(anyString()))
                .willReturn(TestFixtures.alertFalseResponse());

        // Act + Assert
        mockMvc.perform(post("/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestFixtures.webhookRequestJson(objectMapper, "Desconocido", "Hola, ¿cómo están?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alert").value(false));
    }

    // ---- CTRL-02 ----

    @Test
    @DisplayName("CTRL-02: blank user returns 400 with the Bad Request envelope")
    void post_blankUser_returns400WithBadRequestEnvelope() throws Exception {
        // Act + Assert
        mockMvc.perform(post("/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestFixtures.webhookRequestJson(objectMapper, "", "Hola, ¿cómo están?")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("user must not be blank"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ---- CTRL-03 ----

    @Test
    @DisplayName("CTRL-03: blank message returns 400 with the Bad Request envelope")
    void post_blankMessage_returns400WithBadRequestEnvelope() throws Exception {
        // Act + Assert
        mockMvc.perform(post("/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestFixtures.webhookRequestJson(objectMapper, "Desconocido", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("message must not be blank"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ---- CTRL-04 ----

    @Test
    @DisplayName("CTRL-04: malformed JSON returns 400 with 'Malformed JSON request' message")
    void post_malformedJson_returns400WithMalformedJsonMessage() throws Exception {
        // Act + Assert
        mockMvc.perform(post("/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
    }

    // ---- CTRL-05 (no stack-trace leak) ----

    @Test
    @DisplayName("CTRL-05: service throws generic exception — 500 envelope does NOT leak internals")
    void post_serviceThrowsGenericException_returns500AndDoesNotLeakStackTrace() throws Exception {
        // Arrange
        given(alertEvaluatorService.evaluate(anyString()))
                .willThrow(new RuntimeException("DB blew up"));

        // Act + Assert
        mockMvc.perform(post("/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestFixtures.webhookRequestJson(objectMapper, "Desconocido", "Hola, ¿cómo están?")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("Unexpected error occurred"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(not(containsString("RuntimeException"))))
                .andExpect(content().string(not(containsString("Caused by"))))
                .andExpect(content().string(not(containsString("\tat "))))
                .andExpect(content().string(not(containsString("DB blew up"))));
    }

    // ---- CTRL-06 (mock isolation) ----

    @Test
    @DisplayName("CTRL-06: any call invokes the service exactly once with the message")
    void post_anyCall_invokesServiceExactlyOnce() throws Exception {
        // Arrange
        given(alertEvaluatorService.evaluate(anyString()))
                .willReturn(TestFixtures.alertTrueResponse());

        // Act
        mockMvc.perform(post("/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestFixtures.webhookRequestJson(objectMapper, "Desconocido", "Hola, ¿cómo están?")))
                .andExpect(status().isOk());

        // Assert
        verify(alertEvaluatorService, times(1)).evaluate(anyString());
    }

    // ---- CTRL-07 (empty body — pin observed behaviour) ----

    @Test
    @DisplayName("CTRL-07: empty body returns 400 with the Malformed JSON request envelope")
    void post_emptyBody_returns400WithMalformedJsonEnvelope() throws Exception {
        // Spring 6.x (Boot 3.5) treats an empty body with Content-Type: application/json as
        // a missing body — the HttpMessageConverter raises HttpMessageNotReadableException
        // and the GlobalExceptionHandler maps it to the 'Malformed JSON request' envelope.
        // Act + Assert
        mockMvc.perform(post("/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
    }
}
