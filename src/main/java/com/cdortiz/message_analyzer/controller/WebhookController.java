package com.cdortiz.message_analyzer.controller;

import com.cdortiz.message_analyzer.dto.request.WebhookRequest;
import com.cdortiz.message_analyzer.dto.response.AlertResponse;
import com.cdortiz.message_analyzer.service.AlertEvaluatorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives inbound webhook payloads and dispatches them to
 * {@link AlertEvaluatorService} for keyword-based alert evaluation.
 *
 * <p>Validation failures on the request body are translated to HTTP 400
 * responses by
 * {@link com.cdortiz.message_analyzer.exception.GlobalExceptionHandler}.
 * No authentication is enforced on the exposed endpoint.
 */
@RestController
public class WebhookController {

    private AlertEvaluatorService alertEvaluatorService;

    public WebhookController(AlertEvaluatorService alertEvaluatorService) {
        this.alertEvaluatorService = alertEvaluatorService;
    }

    /**
     * Evaluates a webhook message against the configured keyword list and
     * reports whether it should trigger an alert.
     *
     * <p>Status codes:
     * <ul>
     *   <li>{@code 200 OK} — evaluation completed; the boolean
     *       {@link AlertResponse#alert()} flag indicates the verdict.</li>
     *   <li>{@code 400 Bad Request} — the request body failed validation
     *       (blank {@code user} or {@code message}) or was malformed JSON;
     *       produced by
     *       {@link com.cdortiz.message_analyzer.exception.GlobalExceptionHandler}.</li>
     * </ul>
     *
     * @param request inbound payload; both {@code user} and {@code message}
     *                are required and must be non-blank
     * @return HTTP 200 with an {@link AlertResponse} whose {@code alert}
     *         field is {@code true} when the message contains any keyword
     *         configured in
     *         {@link com.cdortiz.message_analyzer.config.AlertProperties}
     */
    @PostMapping("/webhook")
    public ResponseEntity<AlertResponse> handle(@Valid @RequestBody WebhookRequest request){
        return ResponseEntity.ok(alertEvaluatorService.evaluate(request.message()));
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
