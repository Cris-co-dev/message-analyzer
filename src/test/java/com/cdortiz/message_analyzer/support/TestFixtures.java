package com.cdortiz.message_analyzer.support;

import com.cdortiz.message_analyzer.config.AlertProperties;
import com.cdortiz.message_analyzer.dto.request.WebhookRequest;
import com.cdortiz.message_analyzer.dto.response.AlertResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Test-only factory helpers shared by the new test classes.
 *
 * <p>Centralises repeated DTO literals so individual test bodies stay short
 * and the wire shape of {@link WebhookRequest} / {@link AlertResponse} is
 * defined in one place. Methods are exposed as {@code public static} so
 * test classes in sibling packages (e.g. the controller slice) can use
 * them; the class itself is not part of any production API.
 */
public final class TestFixtures {

    private TestFixtures() {
        // utility class — not instantiable
    }

    public static WebhookRequest validWebhookRequest() {
        return new WebhookRequest("Desconocido", "Hola, ¿cómo están?");
    }

    public static WebhookRequest blankUserRequest() {
        return new WebhookRequest("", "Hola, ¿cómo están?");
    }

    public static WebhookRequest blankMessageRequest() {
        return new WebhookRequest("Desconocido", "");
    }

    public static String webhookRequestJson(ObjectMapper mapper, String user, String message)
            throws JsonProcessingException {
        return mapper.writeValueAsString(new WebhookRequest(user, message));
    }

    public static AlertResponse alertTrueResponse() {
        return new AlertResponse(true);
    }

    public static AlertResponse alertFalseResponse() {
        return new AlertResponse(false);
    }

    public static AlertProperties alertProperties(List<String> keywords) {
        return new AlertProperties(keywords);
    }
}
