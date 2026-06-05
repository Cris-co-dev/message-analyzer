// ***********************************************
// Custom commands for the message-analyzer E2E suite.
//
// All commands use `failOnStatusCode: false` on the underlying cy.request
// call so individual tests can assert on 4xx/5xx responses without
// Cypress aborting the run.
// ***********************************************

/**
 * POST a JSON body to the /webhook endpoint.
 *
 * @param {object|string} body  - the request body (object => JSON-encoded by Cypress)
 * @param {object}        [options] - extra options forwarded to cy.request
 * @returns {Cypress.Chainable<Cypress.Response>}
 */
Cypress.Commands.add('postWebhook', (body, options = {}) => {
  return cy.request({
    method: 'POST',
    url: 'http://localhost:8080/webhook',
    body,
    failOnStatusCode: false,
    ...options,
  });
});

/**
 * POST a raw string body to the /webhook endpoint with a custom
 * Content-Type. Used for malformed-JSON / array-body / empty-body tests.
 *
 * @param {string} rawBody
 * @param {string} [contentType='application/json']
 * @returns {Cypress.Chainable<Cypress.Response>}
 */
Cypress.Commands.add('postRaw', (rawBody, contentType = 'application/json') => {
  return cy.request({
    method: 'POST',
    url: 'http://localhost:8080/webhook',
    body: rawBody,
    headers: { 'Content-Type': contentType },
    failOnStatusCode: false,
  });
});

/**
 * Assert that a response is a 200 success with the canonical
 * AlertResponse envelope: exactly { alert: <expected> }.
 *
 * @param {Cypress.Response} response
 * @param {boolean}          expected - the expected alert verdict
 */
Cypress.Commands.add('assertAlertResponse', (response, expected) => {
  expect(response.status, 'status').to.equal(200);
  expect(response.body, 'body keys').to.have.all.keys('alert');
  expect(response.body.alert, 'alert verdict').to.equal(expected);
});

/**
 * Assert that a response is a 4xx/5xx error with the canonical
 * ErrorResponse envelope: exactly { error, message, timestamp }.
 *
 * Also pins:
 *   - `error` label matches the expected string
 *   - `message` matches the supplied matcher (substring or RegExp)
 *   - `timestamp` parses as a valid Date
 *   - no stack-trace / "Caused by" / "at x.y" / "Exception:" internals leak
 *
 * @param {Cypress.Response}    response
 * @param {string}              expectedError - e.g. "Bad Request" / "Internal Server Error"
 * @param {string|RegExp}       [messageMatcher] - optional matcher for the message field
 */
Cypress.Commands.add('assertErrorEnvelope', (response, expectedError, messageMatcher) => {
  expect(response.status, 'status').to.be.oneOf([400, 500]);
  expect(response.body, 'body shape').to.have.all.keys('error', 'message', 'timestamp');
  expect(response.body.error, 'error label').to.equal(expectedError);

  if (messageMatcher instanceof RegExp) {
    expect(response.body.message, 'message').to.match(messageMatcher);
  } else if (typeof messageMatcher === 'string') {
    expect(response.body.message, 'message').to.include(messageMatcher);
  }

  expect(
    new Date(response.body.timestamp).toString(),
    'timestamp is valid Date'
  ).not.to.equal('Invalid Date');

  expect(
    JSON.stringify(response.body),
    'no internals leaked'
  ).not.to.match(/Caused by|at\s+\w+\.\w+|Exception:|RuntimeException/);
});

/**
 * Assert that a response carries a JSON Content-Type header.
 *
 * @param {Cypress.Response} response
 */
Cypress.Commands.add('assertJsonContentType', (response) => {
  expect(response.headers, 'headers').to.have.property('content-type');
  expect(response.headers['content-type'], 'content-type').to.include('application/json');
});
