package com.srm.creditengine.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.creditengine.identity.application.TokenIssuer;
import com.srm.creditengine.identity.domain.IdentityAccount;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * HTTP step definitions for the SRM acceptance suite.
 * Uses TestRestTemplate against the random-port server; captures status, body, and headers
 * into ScenarioState for assertions in DatabaseSteps.
 */
public class ApiSteps {

    private final TestRestTemplate rest;
    private final ScenarioState state;
    private final ObjectMapper mapper;
    private final TokenIssuer tokens;
    private final AcceptanceFxProviderStub fxProvider;
    private final AcceptanceClock clock;
    private final AcceptanceLogCapture logCapture;

    @Autowired
    public ApiSteps(
            TestRestTemplate rest,
            ScenarioState state,
            ObjectMapper mapper,
            TokenIssuer tokens,
            AcceptanceFxProviderStub fxProvider,
            java.time.Clock clock,
            AcceptanceLogCapture logCapture) {
        this.rest = rest;
        this.state = state;
        this.mapper = mapper;
        this.tokens = tokens;
        this.fxProvider = fxProvider;
        this.clock = (AcceptanceClock) clock;
        this.logCapture = logCapture;
    }

    // ─── Lifecycle ──────────────────────────────────────────────────────────────

    @Before
    public void clearScenarioCollections() {
        state.lastHeaders.clear();
        state.statementEntryIds.clear();
        state.concurrentStatuses.clear();
        state.concurrentBodies.clear();
        state.concurrentReplayHeaders.clear();
        state.firstPageEntryIds.clear();
        state.secondPageEntryIds.clear();
        fxProvider.reset();
        logCapture.clear();
    }

    // ─── Authentication ──────────────────────────────────────────────────────────

    @Given("the OPERATOR user is authenticated")
    public void operatorAuthenticated() {
        state.operatorToken = token(
                "00000000-0000-0000-0000-000000000301",
                "operator@srm.local",
                "OPERATOR");
        state.activeToken = state.operatorToken;
    }

    @Given("the ADMIN user is authenticated")
    public void adminAuthenticated() {
        state.adminToken = token(
                "00000000-0000-0000-0000-000000000302",
                "admin@srm.local",
                "ADMIN");
        state.activeToken = state.adminToken;
    }

    private String token(String id, String email, String role) {
        return tokens.issue(new IdentityAccount(
                UUID.fromString(id),
                email,
                "",
                List.of(role)));
    }

    @Then("the protected endpoint role matrix is enforced for every actor role")
    public void assertProtectedEndpointRoleMatrix() {
        Set<String> allRoles = Set.of("OPERATOR", "ANALYST", "ADMIN", "AUDITOR");
        Set<String> operatorAdmin = Set.of("OPERATOR", "ADMIN");
        Set<String> adminOnly = Set.of("ADMIN");
        Set<String> adminAuditor = Set.of("ADMIN", "AUDITOR");
        List<EndpointAccess> endpoints = List.of(
                new EndpointAccess(HttpMethod.POST, "/api/v1/exchange-rates", "{}", adminOnly),
                new EndpointAccess(HttpMethod.GET, "/api/v1/exchange-rates?base=USD&quote=BRL", null, adminOnly),
                new EndpointAccess(HttpMethod.POST, "/api/v1/base-rates", "{}", adminOnly),
                new EndpointAccess(HttpMethod.GET, "/api/v1/base-rates?currency=BRL&effectiveAt=2030-01-15T12:00:00Z", null, adminOnly),
                new EndpointAccess(HttpMethod.POST, "/api/v1/product-spreads", "{}", adminOnly),
                new EndpointAccess(HttpMethod.GET, "/api/v1/product-spreads?productType=MERCANTILE_INVOICE&effectiveAt=2030-01-15T12:00:00Z", null, adminOnly),
                new EndpointAccess(HttpMethod.POST, "/api/v1/fx-sync?base=USD&quote=BRL", "", adminOnly),
                new EndpointAccess(HttpMethod.GET, "/api/v1/conversions?base=USD&quote=BRL&amount=1&at=2030-01-15T12:00:00Z", null, operatorAdmin),
                new EndpointAccess(HttpMethod.POST, "/api/v1/assignors", "{}", operatorAdmin),
                new EndpointAccess(HttpMethod.GET, "/api/v1/assignors", null, allRoles),
                new EndpointAccess(HttpMethod.GET, "/api/v1/assignors/00000000-0000-0000-0000-ffffffffffff", null, allRoles),
                new EndpointAccess(HttpMethod.POST, "/api/v1/receivables", "{}", operatorAdmin),
                new EndpointAccess(HttpMethod.GET, "/api/v1/receivables", null, allRoles),
                new EndpointAccess(HttpMethod.GET, "/api/v1/receivables/00000000-0000-0000-0000-ffffffffffff", null, allRoles),
                new EndpointAccess(HttpMethod.POST, "/api/v1/pricing-simulations", "{}", operatorAdmin),
                new EndpointAccess(HttpMethod.POST, "/api/v1/pricing-quotes", "{}", operatorAdmin),
                new EndpointAccess(HttpMethod.GET, "/api/v1/pricing-quotes/00000000-0000-0000-0000-ffffffffffff", null, allRoles),
                new EndpointAccess(HttpMethod.POST, "/api/v1/settlement-previews", "{}", operatorAdmin),
                new EndpointAccess(HttpMethod.POST, "/api/v1/settlements", "{}", operatorAdmin),
                new EndpointAccess(HttpMethod.GET, "/api/v1/settlements/00000000-0000-0000-0000-ffffffffffff", null, allRoles),
                new EndpointAccess(HttpMethod.POST, "/api/v1/settlements/00000000-0000-0000-0000-ffffffffffff/reversals", "{}", adminOnly),
                new EndpointAccess(HttpMethod.GET, "/api/v1/settlement-statements", null, allRoles),
                new EndpointAccess(HttpMethod.GET, "/api/v1/audit-events", null, adminAuditor),
                new EndpointAccess(HttpMethod.GET, "/api/v1/users/me", null, allRoles),
                new EndpointAccess(HttpMethod.GET, "/actuator/prometheus", null, adminOnly));
        Map<String, String> actorTokens = Map.of(
                "OPERATOR", token("00000000-0000-0000-0000-000000000301", "operator@srm.local", "OPERATOR"),
                "ANALYST", token("00000000-0000-0000-0000-000000000303", "analyst@srm.local", "ANALYST"),
                "ADMIN", token("00000000-0000-0000-0000-000000000302", "admin@srm.local", "ADMIN"),
                "AUDITOR", token("00000000-0000-0000-0000-000000000304", "auditor@srm.local", "AUDITOR"));

        for (EndpointAccess endpoint : endpoints) {
            assertThat(request(endpoint, null).status())
                    .as("anonymous %s %s", endpoint.method(), endpoint.path())
                    .isEqualTo(401);
            if (endpoint.path().startsWith("/api/v1/fx-sync")) {
                fxProvider.enqueue(
                        200,
                        "{\"rate\":\"5.2000000000\",\"observedAt\":\"2030-01-15T12:00:00Z\","
                                + "\"source\":\"role-matrix\"}");
            }
            actorTokens.forEach((role, actorToken) -> {
                int status = request(endpoint, actorToken).status();
                if (endpoint.allowedRoles().contains(role)) {
                    assertThat(status)
                            .as("%s must be authorized for %s %s", role, endpoint.method(), endpoint.path())
                            .isBetween(200, 499)
                            .isNotIn(401, 403);
                } else {
                    assertThat(status)
                            .as("%s must be forbidden for %s %s", role, endpoint.method(), endpoint.path())
                            .isEqualTo(403);
                }
            });
        }
    }

    // ─── Anonymous requests ──────────────────────────────────────────────────────

    @Given("an anonymous POST to {string} with empty JSON body")
    public void anonymousPost(String path) {
        send(HttpMethod.POST, path, "{}", null);
    }

    @Given("an anonymous GET to {string}")
    public void anonymousGet(String path) {
        send(HttpMethod.GET, path, null, null);
    }

    // ─── Generic OPERATOR/ADMIN requests ─────────────────────────────────────────

    @When("the OPERATOR posts to {string} with body {string}")
    public void operatorPost(String path, String body) {
        send(HttpMethod.POST, path, body, state.operatorToken);
    }

    @When("the ADMIN posts to {string} with body {string}")
    public void adminPost(String path, String body) {
        send(HttpMethod.POST, path, body, state.adminToken);
    }

    @When("the OPERATOR gets {string}")
    public void operatorGet(String path) {
        send(HttpMethod.GET, path, null, state.operatorToken);
    }

    @When("the ADMIN gets {string}")
    public void adminGet(String path) {
        send(HttpMethod.GET, path, null, state.adminToken);
    }

    // ─── FX operations ───────────────────────────────────────────────────────────

    @When("the ADMIN records exchange rate {string} as {string} at {string} from {string}")
    public void adminRecordsRate(String pair, String rate, String observedAt, String source) {
        String[] parts = pair.split("/");
        String body = String.format(
                "{\"base\":\"%s\",\"quote\":\"%s\",\"rate\":\"%s\",\"source\":\"%s\",\"observedAt\":\"%s\"}",
                parts[0], parts[1], rate, source, observedAt);
        send(HttpMethod.POST, "/api/v1/exchange-rates", body, state.adminToken);
    }

    @When("the OPERATOR requests conversion of {string} {string} to {string} at {string}")
    public void operatorConversion(String amount, String base, String quote, String at) {
        String path = String.format("/api/v1/conversions?base=%s&quote=%s&amount=%s&at=%s",
                base, quote, amount, at);
        send(HttpMethod.GET, path, null, state.operatorToken);
    }

    @When("the ADMIN triggers FX synchronization for {string} to {string}")
    public void adminFxSync(String base, String quote) {
        send(HttpMethod.POST, "/api/v1/fx-sync?base=" + base + "&quote=" + quote, "", state.adminToken);
    }

    @Given("the FX provider will return transient statuses {string}")
    public void scriptTransientFxStatuses(String statuses) {
        for (String status : statuses.split(",")) {
            fxProvider.enqueue(Integer.parseInt(status.trim()), "{\"error\":\"scripted\"}");
        }
    }

    @Given("the FX provider will return permanent status {int}")
    public void scriptPermanentFxStatus(int status) {
        fxProvider.enqueue(status, "{\"error\":\"scripted\"}");
    }

    @Given("the FX provider will return rate {string} observed at {string}")
    public void scriptFxSuccess(String rate, String observedAt) {
        fxProvider.enqueue(
                200,
                "{\"rate\":\"" + rate + "\",\"observedAt\":\"" + observedAt
                        + "\",\"source\":\"cucumber-provider\"}");
    }

    @When("the FX circuit-open interval elapses")
    public void elapseFxCircuitInterval() {
        clock.advance(Duration.ofSeconds(31));
    }

    @Then("the FX provider request count is {int}")
    public void assertFxProviderRequestCount(int expected) {
        assertThat(fxProvider.requestCount()).isEqualTo(expected);
    }

    // ─── Pricing simulation ───────────────────────────────────────────────────────

    @When("the OPERATOR simulates pricing with face amount {string} {string} product {string} due {string} settlement {string}")
    public void operatorSimulate(String amount, String currency, String product, String due, String settlement) {
        String body = String.format(
                "{\"faceAmount\":\"%s\",\"faceCurrency\":\"%s\",\"productType\":\"%s\",\"dueDate\":\"%s\",\"settlementCurrency\":\"%s\"}",
                amount, currency, product, due, settlement);
        send(HttpMethod.POST, "/api/v1/pricing-simulations", body, state.operatorToken);
    }

    // ─── Receivable creation ──────────────────────────────────────────────────────

    @When("the OPERATOR creates a receivable with amount {string} currency {string} product {string} issue {string} due {string}")
    public void operatorCreateReceivable(String amount, String currency, String product, String issue, String due) {
        UUID id = UUID.randomUUID();
        String body = String.format(
                "{\"id\":\"%s\",\"assignorId\":\"%s\",\"productType\":\"%s\","
                + "\"faceAmount\":\"%s\",\"faceCurrency\":\"%s\",\"issueDate\":\"%s\",\"dueDate\":\"%s\"}",
                id, state.assignorId, product, amount, currency, issue, due);
        send(HttpMethod.POST, "/api/v1/receivables", body, state.operatorToken);
        assertThat(state.lastStatus).isEqualTo(201);
        state.lastReceivableId = id;
    }

    @And("the OPERATOR creates a second receivable with amount {string} currency {string} product {string} issue {string} due {string}")
    public void operatorCreateSecondReceivable(String amount, String currency, String product, String issue, String due) {
        UUID id = UUID.randomUUID();
        String body = String.format(
                "{\"id\":\"%s\",\"assignorId\":\"%s\",\"productType\":\"%s\","
                + "\"faceAmount\":\"%s\",\"faceCurrency\":\"%s\",\"issueDate\":\"%s\",\"dueDate\":\"%s\"}",
                id, state.assignorId, product, amount, currency, issue, due);
        send(HttpMethod.POST, "/api/v1/receivables", body, state.operatorToken);
        assertThat(state.lastStatus).isEqualTo(201);
        state.secondReceivableId = id;
    }

    // ─── Quote creation ────────────────────────────────────────────────────────────

    @When("the OPERATOR creates a pricing quote for the last receivable with settlement currency {string}")
    public void operatorCreateQuote(String settlementCurrency) {
        String body = String.format("{\"receivableId\":\"%s\",\"settlementCurrency\":\"%s\"}",
                state.lastReceivableId, settlementCurrency);
        send(HttpMethod.POST, "/api/v1/pricing-quotes", body, state.operatorToken);
        assertThat(state.lastStatus).isEqualTo(201);
        state.lastQuoteId = UUID.fromString(extractField(state.lastBody, "id"));
    }

    @And("the OPERATOR creates a pricing quote for the first receivable with settlement currency {string}")
    public void operatorCreateFirstQuote(String settlementCurrency) {
        String body = String.format("{\"receivableId\":\"%s\",\"settlementCurrency\":\"%s\"}",
                state.lastReceivableId, settlementCurrency);
        send(HttpMethod.POST, "/api/v1/pricing-quotes", body, state.operatorToken);
        assertThat(state.lastStatus).isEqualTo(201);
        state.lastQuoteId = UUID.fromString(extractField(state.lastBody, "id"));
    }

    @And("the OPERATOR creates a pricing quote for the second receivable with settlement currency {string}")
    public void operatorCreateSecondQuote(String settlementCurrency) {
        String body = String.format("{\"receivableId\":\"%s\",\"settlementCurrency\":\"%s\"}",
                state.secondReceivableId, settlementCurrency);
        send(HttpMethod.POST, "/api/v1/pricing-quotes", body, state.operatorToken);
        assertThat(state.lastStatus).isEqualTo(201);
        state.secondQuoteId = UUID.fromString(extractField(state.lastBody, "id"));
    }

    @And("the OPERATOR creates two quotes for the same receivable with settlement currency {string}")
    public void operatorCreateTwoQuotesSameReceivable(String settlementCurrency) {
        // First quote
        String body1 = String.format("{\"receivableId\":\"%s\",\"settlementCurrency\":\"%s\"}",
                state.lastReceivableId, settlementCurrency);
        send(HttpMethod.POST, "/api/v1/pricing-quotes", body1, state.operatorToken);
        assertThat(state.lastStatus).isEqualTo(201);
        state.lastQuoteId = UUID.fromString(extractField(state.lastBody, "id"));

        // Second quote from the SAME receivable
        String body2 = String.format("{\"receivableId\":\"%s\",\"settlementCurrency\":\"%s\"}",
                state.lastReceivableId, settlementCurrency);
        send(HttpMethod.POST, "/api/v1/pricing-quotes", body2, state.operatorToken);
        assertThat(state.lastStatus).isEqualTo(201);
        state.secondQuoteId = UUID.fromString(extractField(state.lastBody, "id"));
    }

    @When("the OPERATOR retrieves the last pricing quote")
    public void operatorGetQuote() {
        send(HttpMethod.GET, "/api/v1/pricing-quotes/" + state.lastQuoteId, null, state.operatorToken);
    }

    // ─── Settlement preview ────────────────────────────────────────────────────────

    @When("the OPERATOR requests a settlement preview for the last quote")
    public void operatorSettlementPreview() {
        String body = "{\"quoteIds\":[\"" + state.lastQuoteId + "\"]}";
        send(HttpMethod.POST, "/api/v1/settlement-previews", body, state.operatorToken);
    }

    // ─── Settlement ─────────────────────────────────────────────────────────────
    @Given("the settlement idempotency key is {string}")
    public void settlementIdempotencyKey(String key) {
        state.currentIdempotencyKey = key;
    }


    @When("the OPERATOR settles the last quote with idempotency key {string}")
    public void operatorSettle(String key) {
        state.currentIdempotencyKey = key;
        String body = "{\"quoteIds\":[\"" + state.lastQuoteId + "\"]}";
        sendWithIdempotency(HttpMethod.POST, "/api/v1/settlements", body, state.operatorToken, key);
        if (state.lastStatus == 201) {
            state.previousSettlementId = state.lastSettlementId;
            state.lastSettlementId = UUID.fromString(extractField(state.lastBody, "settlementId"));
        }
    }

    @When("the OPERATOR attempts the last quote with idempotency key {string} and correlation {string}")
    public void operatorAttemptsSettlement(String key, String correlationId) {
        state.currentIdempotencyKey = key;
        String body = "{\"quoteIds\":[\"" + state.lastQuoteId + "\"]}";
        sendWithIdempotencyAndCorrelation(
                HttpMethod.POST,
                "/api/v1/settlements",
                body,
                state.operatorToken,
                key,
                correlationId);
    }

    @And("the OPERATOR settles both quotes with idempotency key {string}")
    public void operatorSettleBothQuotes(String key) {
        state.currentIdempotencyKey = key;
        // Build a two-element quote list; order must be distinct UUIDs
        List<String> ids = List.of(state.lastQuoteId.toString(), state.secondQuoteId.toString());
        String quotesJson = "[\"" + String.join("\",\"", ids) + "\"]";
        String body = "{\"quoteIds\":" + quotesJson + "}";
        sendWithIdempotency(HttpMethod.POST, "/api/v1/settlements", body, state.operatorToken, key);
        if (state.lastStatus == 201) {
            state.lastSettlementId = UUID.fromString(extractField(state.lastBody, "settlementId"));
        }
    }

    @When("the OPERATOR attempts to settle both quotes with the same receivable idempotency key {string}")
    public void operatorSettleBothQuotesSameReceivable(String key) {
        state.currentIdempotencyKey = key;
        List<String> ids = List.of(state.lastQuoteId.toString(), state.secondQuoteId.toString());
        String quotesJson = "[\"" + String.join("\",\"", ids) + "\"]";
        String body = "{\"quoteIds\":" + quotesJson + "}";
        sendWithIdempotency(HttpMethod.POST, "/api/v1/settlements", body, state.operatorToken, key);
    }

    // ─── Reversal ─────────────────────────────────────────────────────────────────
    @When("two concurrent OPERATOR requests settle both quotes with idempotency key {string}")
    public void concurrentOperatorSettlement(String key) throws Exception {
        state.currentIdempotencyKey = key;
        String body = "{\"quoteIds\":[\"" + state.lastQuoteId + "\",\"" + state.secondQuoteId + "\"]}";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<HttpAttempt> attempt = () -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return request(
                        HttpMethod.POST,
                        "/api/v1/settlements",
                        body,
                        state.operatorToken,
                        key);
            };
            Future<HttpAttempt> first = executor.submit(attempt);
            Future<HttpAttempt> second = executor.submit(attempt);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<HttpAttempt> future : List.of(first, second)) {
                HttpAttempt result = future.get(20, TimeUnit.SECONDS);
                state.concurrentStatuses.add(result.status());
                state.concurrentBodies.add(result.body());
                state.concurrentReplayHeaders.add(result.replayHeader());
            }
        }
        state.lastStatus = state.concurrentStatuses.getFirst();
        state.lastBody = state.concurrentBodies.getFirst();
        state.lastSettlementId = UUID.fromString(extractField(state.lastBody, "settlementId"));
    }

    @Then("both concurrent responses are byte-identical with exactly one replay")
    public void assertConcurrentSettlementResponses() {
        assertThat(state.concurrentStatuses).containsExactly(201, 201);
        assertThat(state.concurrentBodies).hasSize(2);
        assertThat(state.concurrentBodies.get(0)).isEqualTo(state.concurrentBodies.get(1));
        assertThat(state.concurrentReplayHeaders).containsExactlyInAnyOrder(null, "true");
    }


    @When("the ADMIN reverses the last settlement with reason {string} and key {string}")
    public void adminReverse(String reason, String key) {
        String body = "{\"reason\":\"" + reason + "\"}";
        String path = "/api/v1/settlements/" + state.lastSettlementId + "/reversals";
        sendWithIdempotency(HttpMethod.POST, path, body, state.adminToken, key);
        if (state.lastStatus == 201) {
            state.previousReversalId = state.lastReversalId;
            state.lastReversalId = UUID.fromString(extractField(state.lastBody, "reversalId"));
        }
    }

    // ─── Reporting ─────────────────────────────────────────────────────────────────

    @When("the OPERATOR queries the settlement statement filtered by the last settlement assignor")
    public void operatorQueryStatement() {
        String path = "/api/v1/settlement-statements?assignorId=" + state.assignorId + "&size=50";
        send(HttpMethod.GET, path, null, state.operatorToken);
    }

    @When("the OPERATOR queries the settlement statement with every supported filter")
    public void operatorQueryStatementWithAllFilters() {
        String path = "/api/v1/settlement-statements"
                + "?from=2030-01-15T11:59:00Z"
                + "&to=2030-01-15T12:01:00Z"
                + "&assignorId=" + state.assignorId
                + "&assetCurrency=BRL"
                + "&settlementCurrency=BRL"
                + "&productType=MERCANTILE_INVOICE"
                + "&size=50";
        send(HttpMethod.GET, path, null, state.operatorToken);
    }

    @When("the OPERATOR queries the statement with page {string} size {string}")
    public void operatorQueryStatementPage(String page, String size) throws Exception {
        String path = "/api/v1/settlement-statements?assignorId=" + state.assignorId
                + "&page=" + page + "&size=" + size;
        send(HttpMethod.GET, path, null, state.operatorToken);
        List<String> ids = statementEntryIds();
        if ("0".equals(page)) {
            if (state.firstPageEntryIds.isEmpty()) {
                state.firstPageEntryIds.addAll(ids);
            }
        } else if ("1".equals(page)) {
            state.secondPageEntryIds.clear();
            state.secondPageEntryIds.addAll(ids);
        }
    }

    @When("the OPERATOR requests the first statement page again with size {string}")
    public void operatorRepeatsFirstStatementPage(String size) throws Exception {
        send(
                HttpMethod.GET,
                "/api/v1/settlement-statements?assignorId=" + state.assignorId
                        + "&page=0&size=" + size,
                null,
                state.operatorToken);
        assertThat(statementEntryIds()).containsExactlyElementsOf(state.firstPageEntryIds);
    }

    // ─── Observability ────────────────────────────────────────────────────────────

    @When("the ADMIN retrieves Prometheus metrics")
    public void adminPrometheus() {
        send(HttpMethod.GET, "/actuator/prometheus", null, state.adminToken);
    }

    @When("the OPERATOR invokes a failure with synthetic credentials")
    public void operatorInvokesFailureWithSecrets() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(state.operatorToken);
        headers.set("X-Correlation-ID", "cucumber-safe-correlation");
        headers.set("X-Api-Key", "synthetic-api-key-must-not-leak");
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/runtime/failure?token=synthetic-query-secret",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                String.class);
        state.lastStatus = response.getStatusCode().value();
        state.lastBody = response.getBody() == null ? "" : response.getBody();
        state.lastHeaders.clear();
        response.getHeaders().forEach((key, values) -> state.lastHeaders.put(key, values));
    }

    @And("operational logs contain only bounded fields and no credentials")
    public void assertOperationalLogsAreSafe() {
        var requestEvents = logCapture.events().stream()
                .filter(event -> "HTTP_REQUEST_COMPLETED".equals(event.getFormattedMessage()))
                .toList();
        assertThat(requestEvents).isNotEmpty();
        Set<String> allowedKeys = Set.of(
                "event",
                "method",
                "route",
                "status_class",
                "actor_role",
                "correlation_id");
        for (var event : requestEvents) {
            assertThat(event.getKeyValuePairs()).isNotNull();
            assertThat(event.getKeyValuePairs().stream().map(pair -> pair.key).toList())
                    .isSubsetOf(allowedKeys);
            String rendered = event.getFormattedMessage() + event.getKeyValuePairs();
            assertThat(rendered)
                    .doesNotContain(
                            "database-password",
                            "must-not-leak",
                            "synthetic-api-key-must-not-leak",
                            "synthetic-query-secret",
                            "operator@srm.local",
                            state.operatorToken);
        }
        assertThat(state.lastBody)
                .doesNotContain("database-password", "must-not-leak", "synthetic-query-secret");
    }

    @And("operational logs contain correlation {string}")
    public void assertOperationalLogsContainCorrelation(String correlationId) {
        assertThat(logCapture.events().stream()
                .filter(event -> "HTTP_REQUEST_COMPLETED".equals(event.getFormattedMessage()))
                .flatMap(event -> event.getKeyValuePairs().stream())
                .anyMatch(pair -> "correlation_id".equals(pair.key) && correlationId.equals(pair.value)))
                .isTrue();
    }

    // ─── Response assertions ──────────────────────────────────────────────────────

    @Then("the response status is {int}")
    public void assertStatus(int expectedStatus) {
        assertThat(state.lastStatus).isEqualTo(expectedStatus);
    }

    @And("the response problem code is {string}")
    public void assertProblemCode(String code) {
        assertThat(state.lastBody).contains("\"" + code + "\"");
    }

    @And("the response header {string} is {string}")
    public void assertResponseHeader(String header, String value) {
        List<String> values = state.lastHeaders.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(header))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        assertThat(values).as("header %s", header).isNotNull().isNotEmpty();
        assertThat(values.getFirst()).isEqualTo(value);
    }

    @And("the conversion settlement amount is {string}")
    public void assertConversionSettlementAmount(String expected) {
        assertThat(new BigDecimal(extractField(state.lastBody, "settlementAmount")))
                .isEqualByComparingTo(expected);
    }

    @And("the response contains at least one exchange rate record")
    public void assertExchangeRateNotEmpty() {
        assertThat(state.lastBody).doesNotStartWith("[]");
    }

    @And("the simulation discounted amount is {string}")
    public void assertSimulationDiscountedAmount(String expected) {
        assertThat(extractField(state.lastBody, "discountedAmount")).isEqualTo(expected);
    }

    @And("the simulation settlement amount is {string}")
    public void assertSimulationSettlementAmount(String expected) {
        assertThat(extractField(state.lastBody, "settlementAmount")).isEqualTo(expected);
    }

    @And("the quote status is {string}")
    public void assertQuoteStatus(String expected) {
        assertThat(extractField(state.lastBody, "status")).isEqualTo(expected);
    }

    @And("the quote discounted amount is {string}")
    public void assertQuoteDiscountedAmount(String expected) {
        assertThat(extractPath(state.lastBody, "pricing", "discountedAmount")).isEqualTo(expected);
    }

    @And("the quote settlement amount is {string}")
    public void assertQuoteSettlementAmount(String expected) {
        assertThat(extractPath(state.lastBody, "pricing", "settlementAmount")).isEqualTo(expected);
    }

    @And("the quote face amount is {string}")
    public void assertQuoteFaceAmount(String expected) {
        assertThat(extractPath(state.lastBody, "pricing", "faceAmount")).isEqualTo(expected);
    }

    @And("the settlement status is {string}")
    public void assertSettlementStatus(String expected) {
        assertThat(extractField(state.lastBody, "status")).isEqualTo(expected);
    }

    @And("the settlement id matches the previous settlement")
    public void assertSettlementIdMatches() {
        UUID current = UUID.fromString(extractField(state.lastBody, "settlementId"));
        assertThat(current).isEqualTo(state.previousSettlementId);
    }

    @And("the preview total amount is {string}")
    public void assertPreviewTotal(String expected) {
        assertThat(new BigDecimal(extractField(state.lastBody, "totalAmount")))
                .isEqualByComparingTo(expected);
    }

    @And("the reversal id is captured")
    public void captureReversalId() {
        // already captured in adminReverse step
        assertThat(state.lastReversalId).isNotNull();
    }

    @And("the reversal id matches the previous reversal")
    public void assertReversalIdMatches() {
        UUID current = UUID.fromString(extractField(state.lastBody, "reversalId"));
        assertThat(current).isEqualTo(state.previousReversalId);
    }

    @And("the statement has at least {int} entries for the last settlement assignor")
    public void assertStatementEntries(int minimum) throws Exception {
        JsonNode root = mapper.readTree(state.lastBody);
        int count = root.path("entries").size();
        assertThat(count).isGreaterThanOrEqualTo(minimum);
        state.lastPageSize = count;
    }

    @And("every statement entry matches all requested filters")
    public void assertEveryStatementEntryMatchesFilters() throws Exception {
        JsonNode entries = mapper.readTree(state.lastBody).path("entries");
        assertThat(entries).isNotEmpty();
        for (JsonNode entry : entries) {
            assertThat(entry.path("assignorId").asText()).isEqualTo(state.assignorId.toString());
            assertThat(entry.path("assetCurrency").asText()).isEqualTo("BRL");
            assertThat(entry.path("settlementCurrency").asText()).isEqualTo("BRL");
            assertThat(entry.path("productType").asText()).isEqualTo("MERCANTILE_INVOICE");
            String effectiveAt = entry.path("effectiveAt").asText();
            assertThat(effectiveAt).isGreaterThanOrEqualTo("2030-01-15T11:59:00Z");
            assertThat(effectiveAt).isLessThan("2030-01-15T12:01:00Z");
        }
    }

    @And("the statement settlement entries have positive signed amounts")
    public void assertSettlementEntriesPositive() throws Exception {
        List<BigDecimal> amounts = new ArrayList<>();
        for (JsonNode entry : mapper.readTree(state.lastBody).path("entries")) {
            if ("SETTLEMENT".equals(entry.path("entryType").asText())) {
                amounts.add(new BigDecimal(entry.path("signedAmount").asText()));
            }
        }
        assertThat(amounts).isNotEmpty().allSatisfy(amount -> assertThat(amount).isPositive());
    }

    @And("the statement reversal entries have negative signed amounts")
    public void assertReversalEntriesNegative() throws Exception {
        List<BigDecimal> amounts = new ArrayList<>();
        for (JsonNode entry : mapper.readTree(state.lastBody).path("entries")) {
            if ("REVERSAL".equals(entry.path("entryType").asText())) {
                amounts.add(new BigDecimal(entry.path("signedAmount").asText()));
            }
        }
        assertThat(amounts).isNotEmpty().allSatisfy(amount -> assertThat(amount).isNegative());
    }

    @And("the page has {int} entries")
    public void assertPageEntries(int expected) throws Exception {
        JsonNode root = mapper.readTree(state.lastBody);
        int count = root.path("entries").size();
        assertThat(count).isEqualTo(expected);
        state.lastPageSize = count;
    }

    @And("has next page is {string}")
    public void assertHasNextPage(String expected) {
        boolean hasNext = "true".equals(expected);
        assertThat(extractField(state.lastBody, "hasNext")).isEqualTo(String.valueOf(hasNext));
    }

    @And("the statement entries are ordered by effective time and entry id descending")
    public void assertStatementOrdering() throws Exception {
        List<String> actual = new ArrayList<>();
        for (JsonNode entry : mapper.readTree(state.lastBody).path("entries")) {
            actual.add(entry.path("effectiveAt").asText() + "|" + entry.path("entryId").asText());
        }
        List<String> sorted = new ArrayList<>(actual);
        sorted.sort(java.util.Comparator.reverseOrder());
        assertThat(actual).containsExactlyElementsOf(sorted);
    }

    @And("the second statement page does not overlap the first")
    public void assertStatementPagesDoNotOverlap() {
        assertThat(state.secondPageEntryIds).doesNotContainAnyElementsOf(state.firstPageEntryIds);
    }

    @And("the metrics contain {string} with label product in {string}")
    public void assertMetricProductLabel(String metric, String allowedValues) {
        assertBoundedMetricLabels(metric, "product", allowedValues);
    }

    @And("the metrics contain {string} with label currency in {string}")
    public void assertMetricCurrencyLabel(String metric, String allowedValues) {
        assertBoundedMetricLabels(metric, "currency", allowedValues);
    }

    @And("the metrics contain {string} with label result in {string}")
    public void assertMetricResultLabel(String metric, String allowedValues) {
        assertBoundedMetricLabels(metric, "result", allowedValues);
    }

    @And("the metrics contain {string} with labels currency {string} and result {string}")
    public void assertMetricLabels(String metric, String currency, String result) {
        Pattern seriesPattern =
                Pattern.compile("(?m)^" + Pattern.quote(metric) + "\\{([^}]*)\\}.*$");
        Matcher series = seriesPattern.matcher(state.lastBody);
        while (series.find()) {
            String labels = series.group(1);
            if (labels.contains("currency=\"" + currency + "\"")
                    && labels.contains("result=\"" + result + "\"")) {
                return;
            }
        }
        throw new AssertionError(
                "Missing " + metric + " series with currency=" + currency + " and result=" + result);
    }

    @And("the metrics do not contain raw user input labels")
    public void assertNoRawUserInputLabels() {
        assertThat(state.lastBody)
                .doesNotContain(
                        "operator@srm.local",
                        "admin@srm.local",
                        state.assignorId.toString());
        if (state.lastReceivableId != null) {
            assertThat(state.lastBody).doesNotContain(state.lastReceivableId.toString());
        }
        if (state.lastSettlementId != null) {
            assertThat(state.lastBody).doesNotContain(state.lastSettlementId.toString());
        }
    }

    @And("the metrics contain {string}")
    public void assertMetricPresent(String metric) {
        assertThat(state.lastBody).contains(metric);
    }

    // ─── Internal helpers ──────────────────────────────────────────────────────────

    private void send(HttpMethod method, String path, String body, String token) {
        sendWithIdempotency(method, path, body, token, null);
    }

    private void sendWithIdempotency(
            HttpMethod method,
            String path,
            String body,
            String token,
            String idempotencyKey) {
        sendWithIdempotencyAndCorrelation(method, path, body, token, idempotencyKey, null);
    }

    private void sendWithIdempotencyAndCorrelation(
            HttpMethod method,
            String path,
            String body,
            String token,
            String idempotencyKey,
            String correlationId) {
        HttpAttempt response = request(method, path, body, token, idempotencyKey, correlationId);
        state.lastStatus = response.status();
        state.lastBody = response.body();
        state.lastHeaders.clear();
        response.headers().forEach((key, values) -> state.lastHeaders.put(key, values));
    }

    private HttpAttempt request(EndpointAccess endpoint, String token) {
        String key = endpoint.method() == HttpMethod.POST
                        && endpoint.path().contains("/settlements")
                ? "role-matrix-idempotency-key"
                : null;
        return request(endpoint.method(), endpoint.path(), endpoint.body(), token, key, null);
    }

    private HttpAttempt request(
            HttpMethod method,
            String path,
            String body,
            String token,
            String idempotencyKey) {
        return request(method, path, body, token, idempotencyKey, null);
    }

    private HttpAttempt request(
            HttpMethod method,
            String path,
            String body,
            String token,
            String idempotencyKey,
            String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        if (correlationId != null) {
            headers.set("X-Correlation-ID", correlationId);
        }
        ResponseEntity<String> response =
                rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
        return new HttpAttempt(
                response.getStatusCode().value(),
                response.getBody() == null ? "" : response.getBody(),
                response.getHeaders());
    }

    private List<String> statementEntryIds() throws Exception {
        List<String> ids = new ArrayList<>();
        for (JsonNode entry : mapper.readTree(state.lastBody).path("entries")) {
            ids.add(entry.path("entryId").asText());
        }
        return ids;
    }

    private void assertBoundedMetricLabels(String metric, String label, String allowedValues) {
        Set<String> allowed = new LinkedHashSet<>();
        for (String value : allowedValues.split(",")) {
            allowed.add(value.trim());
        }
        Pattern seriesPattern =
                Pattern.compile("(?m)^" + Pattern.quote(metric) + "\\{([^}]*)\\}.*$");
        Pattern labelPattern =
                Pattern.compile("(?:^|,)" + Pattern.quote(label) + "=\\\"([^\\\"]+)\\\"");
        Matcher series = seriesPattern.matcher(state.lastBody);
        Set<String> observed = new LinkedHashSet<>();
        while (series.find()) {
            Matcher labels = labelPattern.matcher(series.group(1));
            assertThat(labels.find())
                    .as("%s series must have %s label", metric, label)
                    .isTrue();
            observed.add(labels.group(1));
        }
        assertThat(observed)
                .as("bounded %s labels for %s", label, metric)
                .isNotEmpty()
                .isSubsetOf(allowed.toArray(String[]::new));
    }

    /** Extracts a top-level string field from the JSON body. */
    private String extractField(String json, String field) {
        try {
            JsonNode node = mapper.readTree(json);
            JsonNode value = node.path(field);
            if (value.isMissingNode() || value.isNull()) return null;
            return value.isTextual() ? value.asText() : value.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to extract field '" + field + "' from: " + json, e);
        }
    }

    /** Extracts a nested field value from the JSON body using dot-path navigation. */
    private String extractPath(String json, String parent, String field) {
        try {
            JsonNode node = mapper.readTree(json).path(parent).path(field);
            if (node.isMissingNode() || node.isNull()) return null;
            return node.isTextual() ? node.asText() : node.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to extract " + parent + "." + field + " from: " + json, e);
        }
    }

    private record EndpointAccess(
            HttpMethod method,
            String path,
            String body,
            Set<String> allowedRoles) {}

    private record HttpAttempt(int status, String body, HttpHeaders headers) {
        String replayHeader() {
            return headers.getFirst("Idempotent-Replay");
        }
    }
}
