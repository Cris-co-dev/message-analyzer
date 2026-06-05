/// <reference types="cypress" />

describe('Message Analyzer — POST /webhook', () => {

  // -------------------------------------------------------------------
  // Constants — mirrors application.yaml exactly (45 Spanish keywords)
  // -------------------------------------------------------------------
  const ENDPOINT = 'http://localhost:8080/webhook';

  const URGENCY_KEYWORDS = [
    'Urgente', 'Prioritario', 'Crítico', 'Inmediato', 'Apremiante',
    'Imperativo', 'Inaplazable', 'Importante', 'Prioridad alta',
    'Atención inmediata', 'Acción requerida', 'Tiempo sensible',
    'De máxima prioridad', 'Emergente', 'Preferente',
  ];
  const ERROR_KEYWORDS = [
    'Error', 'Falla', 'Incidencia', 'Problema', 'Defecto',
    'Anomalía', 'Excepción', 'Inconveniente', 'Bug',
    'Mal funcionamiento', 'Interrupción', 'Desviación',
    'Error técnico', 'Comportamiento inesperado', 'Evento de fallo',
  ];
  const HELP_KEYWORDS = [
    'Ayuda', 'Soporte', 'Asistencia', 'Colaboración', 'Acompañamiento',
    'Orientación', 'Asesoría', 'Respaldo', 'Atención', 'Apoyo',
    'Servicio', 'Consulta', 'Guía', 'Intervención', 'Cooperación',
  ];
  const ALL_KEYWORDS = [...URGENCY_KEYWORDS, ...ERROR_KEYWORDS, ...HELP_KEYWORDS];

  // Slugify a keyword for inclusion in a test name:
  //   "Acción requerida" -> "Accion_requerida"
  //   "Crítico"         -> "Critico"
  const slug = (s) =>
    s
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^A-Za-z0-9]+/g, '_')
      .replace(/^_+|_+$/g, '');

  // ===============================================================
  // 1. Happy path — alert=true
  // ===============================================================
  context('Happy path — alert evaluation (200, alert=true)', () => {

    it('alert_isTrue_whenMessageContainsUrgenteCaseInsensitive', () => {
      cy.postWebhook({ user: 'Juan', message: 'esto es urgente resolver' })
        .then((r) => cy.assertAlertResponse(r, true));
    });

    it('alert_isTrue_whenMessageContainsPrioridadAlta_multiwordKeyword', () => {
      cy.postWebhook({ user: 'María', message: 'manejo con prioridad alta' })
        .then((r) => cy.assertAlertResponse(r, true));
    });

    it('alert_isTrue_whenMessageContainsAtencionInmediata_multiwordKeyword', () => {
      cy.postWebhook({ user: 'Carlos', message: 'requiere atención inmediata' })
        .then((r) => cy.assertAlertResponse(r, true));
    });

    it('alert_isTrue_whenMessageContainsAccentedKeyword_Critico', () => {
      cy.postWebhook({ user: 'Ana', message: 'es un caso crítico para el cliente' })
        .then((r) => cy.assertAlertResponse(r, true));
    });

    it('alert_isTrue_whenMessageContainsAllCapsKeyword_URGENTE', () => {
      cy.postWebhook({ user: 'Luis', message: 'ESTO ES URGENTE' })
        .then((r) => cy.assertAlertResponse(r, true));
    });

    it('alert_isTrue_whenMessageContainsLowercaseAccentedKeyword_critico_SVC05a_pin', () => {
      cy.postWebhook({ user: 'Sofía', message: 'esto es crítico hoy' })
        .then((r) => cy.assertAlertResponse(r, true));
    });

    it('alert_isTrue_whenMessageContainsKeywordFromErrorsCategory_Error', () => {
      cy.postWebhook({ user: 'Diego', message: 'hubo un error en producción' })
        .then((r) => cy.assertAlertResponse(r, true));
    });

    it('alert_isTrue_whenMessageContainsKeywordFromHelpCategory_Ayuda', () => {
      cy.postWebhook({ user: 'Desconocido', message: 'necesito ayuda con esto' })
        .then((r) => cy.assertAlertResponse(r, true));
    });

    it('alert_isTrue_whenKeywordAtStartOfMessage', () => {
      cy.postWebhook({ user: 'Desconocido', message: 'Urgente: revisar el ticket' })
        .then((r) => cy.assertAlertResponse(r, true));
    });

    it('alert_isTrue_whenKeywordAtMiddleOfMessage', () => {
      cy.postWebhook({ user: 'Desconocido', message: 'el ticket es Urgente y necesita' })
        .then((r) => cy.assertAlertResponse(r, true));
    });

    it('alert_isTrue_whenKeywordAtEndOfMessage', () => {
      cy.postWebhook({ user: 'Desconocido', message: 'por favor revisar, es Urgente' })
        .then((r) => cy.assertAlertResponse(r, true));
    });

    it('alert_isTrue_whenKeywordSurroundedByPunctuation', () => {
      cy.postWebhook({ user: 'Desconocido', message: '¡Urgente! ¿Puedes revisarlo? Gracias.' })
        .then((r) => cy.assertAlertResponse(r, true));
    });

    it('alert_isTrue_whenMessageIsVeryLongWithKeywordEmbedded', () => {
      const padding = 'este es un texto de relleno '.repeat(60);
      const message = padding + 'URGENTE' + ' '.repeat(40) + 'fin';
      cy.postWebhook({ user: 'Desconocido', message })
        .then((r) => cy.assertAlertResponse(r, true));
    });

    it('alert_isTrue_whenMessageContainsMultipleDifferentKeywords', () => {
      cy.postWebhook({
        user: 'Desconocido',
        message: 'Urgente: error en producción, necesito ayuda inmediata',
      }).then((r) => cy.assertAlertResponse(r, true));
    });

    it('alert_isTrue_whenMessageContainsAllThreeCategoriesTogether', () => {
      cy.postWebhook({
        user: 'Desconocido',
        message: 'Urgente error requiere ayuda',
      }).then((r) => cy.assertAlertResponse(r, true));
    });

    it('alert_isTrue_whenMessageHasKeywordAcrossNewlineBoundaries', () => {
      cy.postWebhook({
        user: 'Desconocido',
        message: 'por favor\nUrgente\nrevisar',
      }).then((r) => cy.assertAlertResponse(r, true));
    });

    it('alert_isTrue_whenMessageHasKeywordWithLeadingAndTrailingSpaces', () => {
      cy.postWebhook({ user: 'Desconocido', message: '   Urgente   ' })
        .then((r) => cy.assertAlertResponse(r, true));
    });

    it('alert_isTrue_whenUserIsIrrelevantAndMessageContainsKeyword', () => {
      cy.postWebhook({ user: 'cualquiera', message: 'esto es urgente' })
        .then((r) => cy.assertAlertResponse(r, true));
    });

    // -------------------------------------------------------
    // Full keyword matrix — one test per configured keyword.
    // Catches drift if anyone removes/edits application.yaml.
    // -------------------------------------------------------
    ALL_KEYWORDS.forEach((keyword) => {
      it(`alert_isTrue_whenMessageContainsConfiguredKeyword_${slug(keyword)}`, () => {
        cy.postWebhook({
          user: 'Desconocido',
          message: `Mensaje de prueba: ${keyword} detectado en el texto.`,
        }).then((r) => cy.assertAlertResponse(r, true));
      });
    });

    it('alert_isTrue_whenMessageContainsAllConfiguredKeywordsConcatenated', () => {
      const message = ALL_KEYWORDS.map((k) => `[${k}]`).join(' ');
      cy.postWebhook({ user: 'Desconocido', message })
        .then((r) => cy.assertAlertResponse(r, true));
    });
  });

  // ===============================================================
  // 2. Happy path — alert=false
  // ===============================================================
  context('Happy path — alert evaluation (200, alert=false)', () => {

    it('alert_isFalse_whenMessageHasNoKeyword', () => {
      cy.postWebhook({ user: 'Desconocido', message: 'Hola como estan todos?' })
        .then((r) => cy.assertAlertResponse(r, false));
    });

    it('alert_isTrue_whenAccentlessVersionOfAccentedKeyword_SVC05b_pin', () => {
      cy.postWebhook({ user: 'Desconocido', message: 'esto es critico hoy' })
        .then((r) => cy.assertAlertResponse(r, true));
    });

    it('alert_isFalse_whenUserContainsKeywordButMessageDoesNot', () => {
      // Proves the service evaluates only the `message` field.
      cy.postWebhook({ user: 'Urgente', message: 'hola como estan' })
        .then((r) => cy.assertAlertResponse(r, false));
    });

    it('alert_isFalse_whenMessageContainsOnlyNonKeywordSpanishWords', () => {
      cy.postWebhook({ user: 'Desconocido', message: 'buenos dias, todo en orden' })
        .then((r) => cy.assertAlertResponse(r, false));
    });
  });

  // ===============================================================
  // 3. Accent & case-folding pins
  // ===============================================================
  context('Accent & case-folding pins (mirrors SVC-05a/b)', () => {

    it('accent_critico_with_unicode_í_matches_keyword_Critico', () => {
      cy.postWebhook({ user: 'Desconocido', message: 'crítico' })
        .then((r) => cy.assertAlertResponse(r, true));
    });

    it('accent_critico_without_unicode_í_now_MATCHES_keyword_Critico_pinsNewBehavior', () => {
      cy.postWebhook({ user: 'Desconocido', message: 'critico' })
        .then((r) => cy.assertAlertResponse(r, true));
    });

    it('accent_multiwordKeyword_AtencionInmediata_matchesUnaccentedMessageAtencionInmediata', () => {
      cy.postWebhook({ user: 'Desconocido', message: 'requiere atencion inmediata' })
        .then((r) => cy.assertAlertResponse(r, true));
    });

    it('accent_multiwordKeyword_AccionRequerida_matchesUnaccentedMessageAccionRequerida', () => {
      cy.postWebhook({ user: 'Desconocido', message: 'por favor, accion requerida' })
        .then((r) => cy.assertAlertResponse(r, true));
    });

    it('accent_keywordInMsgAnomalia_matchesKeywordInYamlAnomalia_caseAndAccentInsensitive', () => {
      // "Anomalía" in YAML has an accent; "anomalia" in the message does not.
      cy.postWebhook({ user: 'Desconocido', message: 'hubo una anomalia en el sistema' })
        .then((r) => cy.assertAlertResponse(r, true));
    });

    it('accent_keywordInMsgCooperacion_matchesKeywordInYamlCooperacion', () => {
      // "Cooperación" in YAML has an accent; "cooperacion" in the message does not.
      cy.postWebhook({ user: 'Desconocido', message: 'solicito cooperacion del equipo' })
        .then((r) => cy.assertAlertResponse(r, true));
    });

    it('case_allCaps_URGENTE_matches_lowercaseKeyword', () => {
      cy.postWebhook({ user: 'Desconocido', message: 'URGENTE' })
        .then((r) => cy.assertAlertResponse(r, true));
    });

    it('case_mixedCase_UrGeNtE_matches_lowercaseKeyword', () => {
      cy.postWebhook({ user: 'Desconocido', message: 'UrGeNtE' })
        .then((r) => cy.assertAlertResponse(r, true));
    });
  });

  // ===============================================================
  // 4. Validation — blank/missing fields (400)
  // ===============================================================
  context('Validation — blank/missing fields (400)', () => {

    it('validation_returns400WithBadRequestEnvelope_whenUserIsBlank', () => {
      cy.postWebhook({ user: '', message: 'hola' })
        .then((r) => cy.assertErrorEnvelope(r, 'Bad Request', 'user must not be blank'));
    });

    it('validation_returns400WithBadRequestEnvelope_whenUserIsWhitespaceOnly', () => {
      cy.postWebhook({ user: '   ', message: 'hola' })
        .then((r) => cy.assertErrorEnvelope(r, 'Bad Request', 'user must not be blank'));
    });

    it('validation_returns400WithBadRequestEnvelope_whenMessageIsBlank', () => {
      cy.postWebhook({ user: 'Desconocido', message: '' })
        .then((r) => cy.assertErrorEnvelope(r, 'Bad Request', 'message must not be blank'));
    });

    it('validation_returns400WithBadRequestEnvelope_whenMessageIsWhitespaceOnly', () => {
      cy.postWebhook({ user: 'Desconocido', message: '   ' })
        .then((r) => cy.assertErrorEnvelope(r, 'Bad Request', 'message must not be blank'));
    });

    it('validation_returns400WithBadRequestEnvelope_whenBothFieldsAreBlank', () => {
      cy.postWebhook({ user: '', message: '' })
        .then((r) => {
          // At least one of the two violations is surfaced (controller
          // picks the first field error; the order is deterministic but
          // not contractual).
          expect(r.status).to.equal(400);
          expect(r.body.error).to.equal('Bad Request');
          expect(r.body.message).to.match(/must not be blank/);
          cy.assertErrorEnvelope(r, 'Bad Request', /must not be blank/);
        });
    });

    it('validation_returns400WithBadRequestEnvelope_whenUserFieldIsOmitted', () => {
      cy.postWebhook({ message: 'hola' })
        .then((r) => cy.assertErrorEnvelope(r, 'Bad Request', 'user must not be blank'));
    });

    it('validation_returns400WithBadRequestEnvelope_whenMessageFieldIsOmitted', () => {
      cy.postWebhook({ user: 'Desconocido' })
        .then((r) => cy.assertErrorEnvelope(r, 'Bad Request', 'message must not be blank'));
    });

    it('validation_returns400WithMalformedJsonEnvelope_whenUserIsWrongType_object', () => {
      // Using an object (not a string) forces Jackson to reject deserialization
      // → HttpMessageNotReadableException → "Malformed JSON request".
      cy.postWebhook({ user: { nested: 'object' }, message: 'hola' })
        .then((r) => cy.assertErrorEnvelope(r, 'Bad Request', 'Malformed JSON request'));
    });
  });

  // ===============================================================
  // 5. Malformed request (400)
  // ===============================================================
  context('Malformed request (400)', () => {

    it('malformed_returns400WithMalformedJsonEnvelope_whenBodyIsBrokenJson', () => {
      cy.postRaw('{not json', 'application/json')
        .then((r) => cy.assertErrorEnvelope(r, 'Bad Request', 'Malformed JSON request'));
    });

    it('malformed_returns400WithMalformedJsonEnvelope_whenBodyIsEmptyString', () => {
      cy.postRaw('', 'application/json')
        .then((r) => cy.assertErrorEnvelope(r, 'Bad Request', 'Malformed JSON request'));
    });

    it('malformed_returns400WithMalformedJsonEnvelope_whenBodyIsJsonArray', () => {
      // Array is valid JSON but not a WebhookRequest object.
      cy.postRaw('[]', 'application/json')
        .then((r) => cy.assertErrorEnvelope(r, 'Bad Request', 'Malformed JSON request'));
    });

    it('malformed_returns400WithMalformedJsonEnvelope_whenBodyIsJsonString', () => {
      // Top-level string is valid JSON but not an object.
      cy.postRaw('"hello"', 'application/json')
        .then((r) => cy.assertErrorEnvelope(r, 'Bad Request', 'Malformed JSON request'));
    });

    it('malformed_returns400WithMalformedJsonEnvelope_whenNoBodyIsSent', () => {
      // No body at all — Spring 6.x / Boot 3.5 raises HttpMessageNotReadable
      // because the required request body is missing.
      cy.request({
        method: 'POST',
        url: ENDPOINT,
        failOnStatusCode: false,
      }).then((r) => cy.assertErrorEnvelope(r, 'Bad Request', 'Malformed JSON request'));
    });
  });

  // ===============================================================
  // 6. Response envelope shape
  // ===============================================================
  context('Response envelope shape', () => {

    it('envelope_successBody_hasExactlyAlertKey_noExtras', () => {
      cy.postWebhook({ user: 'Desconocido', message: 'hola' })
        .then((r) => {
          expect(Object.keys(r.body), 'success body keys').to.deep.equal(['alert']);
        });
    });

    it('envelope_successContentType_isApplicationJson', () => {
      cy.postWebhook({ user: 'Desconocido', message: 'hola' })
        .then((r) => cy.assertJsonContentType(r));
    });

    it('envelope_errorBody_hasExactlyErrorMessageTimestampKeys', () => {
      cy.postWebhook({ user: '', message: 'hola' })
        .then((r) => {
          expect(Object.keys(r.body).sort(), 'error body keys')
            .to.deep.equal(['error', 'message', 'timestamp']);
        });
    });

    it('envelope_errorTimestamp_parsesAsValidIsoDate', () => {
      cy.postWebhook({ user: '', message: 'hola' })
        .then((r) => {
          expect(r.body.timestamp, 'timestamp is a non-empty string').to.be.a('string').and.not.be.empty;
          const parsed = new Date(r.body.timestamp);
          expect(parsed.toString(), 'timestamp parses to valid Date')
            .not.to.equal('Invalid Date');
        });
    });

    it('envelope_errorResponse_doesNotLeakStackTrace_orInternals', () => {
      // Mirrors the JUnit CTRL-05 contract: no Caused by / \tat / Exception:
      // / RuntimeException substrings in the error body.
      cy.postWebhook({ user: '', message: 'hola' })
        .then((r) => {
          const serialized = JSON.stringify(r.body);
          expect(serialized, 'no "Caused by" leak').not.to.include('Caused by');
          expect(serialized, 'no "at x.y" stack-frame leak')
            .not.to.match(/at\s+\w+\.\w+/);
          expect(serialized, 'no "Exception:" leak').not.to.include('Exception:');
          expect(serialized, 'no "RuntimeException" leak').not.to.include('RuntimeException');
        });
    });
  });

  // ===============================================================
  // 7. HTTP semantics
  // ===============================================================
  context('HTTP semantics', () => {

    it('http_get_webhook_returns405MethodNotAllowed', () => {
      cy.request({ method: 'GET', url: ENDPOINT, failOnStatusCode: false })
        .then((r) => {
          expect(r.status, 'GET status').to.equal(405);
        });
    });

    it('http_put_webhook_returns405MethodNotAllowed', () => {
      cy.request({ method: 'PUT', url: ENDPOINT, failOnStatusCode: false })
        .then((r) => {
          expect(r.status, 'PUT status').to.equal(405);
        });
    });

    it('http_delete_webhook_returns405MethodNotAllowed', () => {
      cy.request({ method: 'DELETE', url: ENDPOINT, failOnStatusCode: false })
        .then((r) => {
          expect(r.status, 'DELETE status').to.equal(405);
        });
    });

    it('http_patch_webhook_returns405MethodNotAllowed', () => {
      cy.request({ method: 'PATCH', url: ENDPOINT, failOnStatusCode: false })
        .then((r) => {
          expect(r.status, 'PATCH status').to.equal(405);
        });
    });

    it('http_post_nonexistentPath_returns404', () => {
      cy.request({
        method: 'POST',
        url: 'http://localhost:8080/webhook/nonexistent',
        body: { user: 'Desconocido', message: 'hola' },
        failOnStatusCode: false,
      }).then((r) => {
        expect(r.status, 'POST /webhook/nonexistent status').to.equal(404);
      });
    });

    it('http_options_webhook_doesNotReturnServerError', () => {
      // Spring's default CORS handling is implementation-defined; we only
      // assert it is not a 5xx (no leaked crash) and not a 404 (path is
      // mapped).
      cy.request({ method: 'OPTIONS', url: ENDPOINT, failOnStatusCode: false })
        .then((r) => {
          expect(r.status, 'OPTIONS status').to.be.lessThan(500);
          expect(r.status, 'OPTIONS status').not.to.equal(404);
        });
    });
  });

  // ===============================================================
  // 8. Idempotency & state
  // ===============================================================
  context('Idempotency & state', () => {

    it('idempotency_consecutiveRequestsWithKeyword_bothAlertTrue', () => {
      const body = { user: 'Desconocido', message: 'es urgente' };
      cy.postWebhook(body).then((r1) => cy.assertAlertResponse(r1, true));
      cy.postWebhook(body).then((r2) => cy.assertAlertResponse(r2, true));
    });

    it('idempotency_consecutiveRequestsWithoutKeyword_bothAlertFalse', () => {
      const body = { user: 'Desconocido', message: 'hola como estan' };
      cy.postWebhook(body).then((r1) => cy.assertAlertResponse(r1, false));
      cy.postWebhook(body).then((r2) => cy.assertAlertResponse(r2, false));
    });

    it('idempotency_alternatingKeywordAndNoKeyword_eachCallIndependent', () => {
      cy.postWebhook({ user: 'Desconocido', message: 'es urgente' })
        .then((r) => cy.assertAlertResponse(r, true));
      cy.postWebhook({ user: 'Desconocido', message: 'hola como estan' })
        .then((r) => cy.assertAlertResponse(r, false));
      cy.postWebhook({ user: 'Desconocido', message: 'hay un error' })
        .then((r) => cy.assertAlertResponse(r, true));
      cy.postWebhook({ user: 'Desconocido', message: 'todo bien' })
        .then((r) => cy.assertAlertResponse(r, false));
    });

    it('performance_responseTime_isUnder5Seconds', () => {
      cy.postWebhook({ user: 'Desconocido', message: 'hola' })
        .then((r) => {
          expect(r.status, 'status').to.equal(200);
          expect(r.duration, 'response duration (ms)').to.be.lessThan(5000);
        });
    });
  });

  // ===============================================================
  // 9. Behavioral edge cases
  // ===============================================================
  context('Behavioral edge cases', () => {

    it('edge_bodyWithExtraFields_isAccepted_ignoredByJackson', () => {
      // Jackson tolerates unknown fields by default. The response is the
      // same canonical { alert } envelope.
      cy.postWebhook({
        user: 'Desconocido',
        message: 'hola',
        extra: 'ignorado',
        nested: { foo: 'bar' },
      }).then((r) => cy.assertAlertResponse(r, false));
    });

    it('edge_unicodeCharactersInMessage_doNotCrash', () => {
      cy.postWebhook({
        user: 'Desconocido',
        message: '🚨 alerta con emoji y caracteres acentuados: ñáéíóú',
      }).then((r) => {
        expect(r.status, 'status').to.equal(200);
        expect(r.body.alert, 'no Spanish keyword present').to.equal(false);
      });
    });

    it('edge_messageWithOnlyEmojis_doesNotCrash_andReturnsFalse', () => {
      cy.postWebhook({ user: 'Desconocido', message: '🎉🚀💥' })
        .then((r) => cy.assertAlertResponse(r, false));
    });

    it('edge_numericStringMessage_doesNotCrash_andReturnsFalse', () => {
      cy.postWebhook({ user: 'Desconocido', message: '1234567890' })
        .then((r) => cy.assertAlertResponse(r, false));
    });

    it('edge_veryLongUserField_isAccepted', () => {
      const longUser = 'a'.repeat(2000);
      cy.postWebhook({ user: longUser, message: 'hola' })
        .then((r) => cy.assertAlertResponse(r, false));
    });
  });

});
