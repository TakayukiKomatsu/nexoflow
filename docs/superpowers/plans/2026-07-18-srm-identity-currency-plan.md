# SRM Identity and Currency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make authorization, supported-currency handling, immutable FX observations, provider resilience, and authenticated operational evidence conform to SDD 03 and the permission matrix.

**Architecture:** Validate currency codes before every conversion path, distinguish missing from stale observations with typed domain errors, and enforce immutability in PostgreSQL. Keep retry/circuit behavior inside the HTTP adapter and log authenticated actors from a filter placed after bearer authentication.

**Tech Stack:** Spring Security, OAuth2 Resource Server JWT, Spring JDBC, PostgreSQL/Flyway, Spring RestClient, Micrometer, JUnit 5, Testcontainers.

## Global Constraints

- Supported currencies are exactly BRL and USD.
- Exactly 24-hour-old FX is valid; older FX is stale.
- Retry only transport and HTTP 5xx failures; never retry 4xx or invalid payloads.
- Metrics use bounded domain labels only.
- Logs never include credentials, JWTs, request bodies, idempotency keys, or business identifiers.
- New migrations are append-only.

---

### Task 1: Introduce supported-currency and FX failure types

**Files:**
- Create: `backend/src/main/java/com/srm/creditengine/currency/SupportedCurrency.java`
- Create: `backend/src/main/java/com/srm/creditengine/currency/application/UnsupportedCurrencyException.java`
- Create: `backend/src/main/java/com/srm/creditengine/currency/application/FxRateMissingException.java`
- Create: `backend/src/main/java/com/srm/creditengine/currency/application/FxRateStaleException.java`
- Modify: `backend/src/main/java/com/srm/creditengine/currency/application/JdbcCurrencyService.java`
- Test: `backend/src/test/java/com/srm/creditengine/currency/application/JdbcCurrencyServiceTest.java`

**Interfaces:**
- Produces: `SupportedCurrency.require(String): String` returning canonical `BRL` or `USD`.
- Produces: stable codes `UNSUPPORTED_CURRENCY`, `FX_RATE_MISSING`, and `FX_RATE_STALE`.

- [ ] **Step 1: Add failing conversion tests**

Add tests proving:

```java
assertThatThrownBy(() -> service.resolveConversion("EUR", "EUR", new BigDecimal("100.00"), now))
        .isInstanceOf(UnsupportedCurrencyException.class);
assertThatThrownBy(() -> service.resolveConversion("BRL", "USD", new BigDecimal("100.00"), now))
        .isInstanceOf(FxRateMissingException.class);
```

Insert an observation at `now.minus(Duration.ofHours(24))` and assert conversion succeeds. Insert only one at `now.minus(Duration.ofHours(24)).minusNanos(1)` and assert `FxRateStaleException`.

- [ ] **Step 2: Run the focused tests red**

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*JdbcCurrencyServiceTest'
```

Expected: FAIL because unsupported equal currencies still return identity and typed FX failures do not exist.

- [ ] **Step 3: Implement canonical currency validation**

Create:

```java
package com.srm.creditengine.currency;

import com.srm.creditengine.currency.application.UnsupportedCurrencyException;
import java.util.Locale;
import java.util.Set;

public final class SupportedCurrency {
    private static final Set<String> VALUES = Set.of("BRL", "USD");
    private SupportedCurrency() {}

    public static String require(String value) {
        if (value == null) throw new UnsupportedCurrencyException();
        String canonical = value.trim().toUpperCase(Locale.ROOT);
        if (!VALUES.contains(canonical)) throw new UnsupportedCurrencyException();
        return canonical;
    }
}
```

Each exception has a stable safe message and no request data.

- [ ] **Step 4: Distinguish missing from stale in JDBC**

At the start of `recordObservation`, `observations`, and `resolveConversion`, call `SupportedCurrency.require` for both codes. Replace nullable `latest` semantics with:

```java
private Observation latest(String base, String quote, Instant at) {
    List<Observation> values = jdbc.query(LATEST_SQL, mapper, base, quote, Timestamp.from(at));
    if (values.isEmpty()) throw new FxRateMissingException();
    Observation latest = values.getFirst();
    if (latest.observedAt().isBefore(at.minus(MAX_AGE))) throw new FxRateStaleException();
    return latest;
}
```

For direct/inverse lookup, query both orientations before deciding: no rows in either orientation means missing; rows in either orientation but none fresh means stale. Identity conversion still returns rate `1`, source `IDENTITY`, and a two-decimal settlement amount, but only after both codes validate.

- [ ] **Step 5: Hand off stable Problem Detail mappings**

Send the Settlement/Reporting owner the three exception class names and these fixed mappings:

```text
UnsupportedCurrencyException -> 400 UNSUPPORTED_CURRENCY
FxRateMissingException       -> 422 FX_RATE_MISSING
FxRateStaleException         -> 422 FX_RATE_STALE
```

That owner is the sole editor of `ApiExceptionHandler.java` and `ApiErrorContractTest.java`, preventing a shared-file collision between parallel lanes.

- [ ] **Step 6: Verify focused contracts**

Run the Step 2 command. Expected: PASS, including the exact 24-hour boundary.

- [ ] **Step 7: Commit the domain correction**

```bash
git add -- backend/src/main/java/com/srm/creditengine/currency backend/src/test/java/com/srm/creditengine/currency/application/JdbcCurrencyServiceTest.java
git commit -m "fix(currency): enforce supported and fresh FX rates"
```

### Task 1A: Audit reference-data mutations

**Files:**
- Create: `backend/src/main/java/com/srm/creditengine/audit/application/AuditEventRecorder.java`
- Create: `backend/src/main/java/com/srm/creditengine/audit/application/JdbcAuditEventRecorder.java`
- Modify: `backend/src/main/java/com/srm/creditengine/currency/application/ReferenceRateService.java`
- Modify: `backend/src/main/java/com/srm/creditengine/currency/application/JdbcReferenceRateService.java`
- Modify: `backend/src/main/java/com/srm/creditengine/currency/application/JdbcCurrencyService.java`
- Modify: `backend/src/main/java/com/srm/creditengine/currency/api/ReferenceRateController.java`
- Modify: `backend/src/main/java/com/srm/creditengine/currency/api/ExchangeRateController.java`
- Test: `backend/src/test/java/com/srm/creditengine/currency/application/ReferenceRateServiceTest.java`
- Test: `backend/src/test/java/com/srm/creditengine/currency/application/JdbcCurrencyServiceTest.java`

**Interfaces:**
- Produces: `AuditEventRecorder.record(String actor, String action, String targetType, UUID targetId, Instant occurredAt)`.
- Changes: `ReferenceRateService.recordBaseRate` and `recordProductSpread` accept the authenticated actor.

- [ ] **Step 1: Add failing audit assertions**

After each exchange-rate, base-rate, and product-spread write, query `audit_events` by the generated target ID and assert exactly one row with the authenticated actor, bounded action, target type, occurrence time, correlation ID when available, and empty safe metadata. Assert no rate, payload, token, email inside metadata, or credential is stored.

- [ ] **Step 2: Run focused tests red**

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*ReferenceRateServiceTest' --tests '*JdbcCurrencyServiceTest'
```

- [ ] **Step 3: Add the focused audit recorder**

Implement:

```java
public interface AuditEventRecorder {
    void record(String actor, String action, String targetType, UUID targetId, Instant occurredAt);
}
```

The JDBC implementation inserts one append-only `audit_events` row with `MDC.get("correlationId")` and `'{}'::jsonb`. It rejects blank actors/actions/types and null target/time before executing SQL.

- [ ] **Step 4: Pass actors through reference writes**

Change the application signatures to:

```java
void recordBaseRate(String currency, BigDecimal monthlyRate, Instant effectiveAt, String actor);
void recordProductSpread(String productType, BigDecimal monthlySpread, Instant effectiveAt, String actor);
```

The controllers obtain `actors.currentActor().email()`. Each JDBC service generates the domain row ID once, inserts the reference row, then records the corresponding `EXCHANGE_RATE_RECORDED`, `BASE_RATE_RECORDED`, or `PRODUCT_SPREAD_RECORDED` event in the same transaction.

- [ ] **Step 5: Verify transactional audit**

Run the Step 2 command. Add a failure-injection assertion that a rejected reference insert leaves no audit row.

- [ ] **Step 6: Commit reference auditing**

```bash
git add -- backend/src/main/java/com/srm/creditengine/audit/application backend/src/main/java/com/srm/creditengine/currency/application/ReferenceRateService.java backend/src/main/java/com/srm/creditengine/currency/application/JdbcReferenceRateService.java backend/src/main/java/com/srm/creditengine/currency/application/JdbcCurrencyService.java backend/src/main/java/com/srm/creditengine/currency/api/ReferenceRateController.java backend/src/main/java/com/srm/creditengine/currency/api/ExchangeRateController.java backend/src/test/java/com/srm/creditengine/currency/application/ReferenceRateServiceTest.java backend/src/test/java/com/srm/creditengine/currency/application/JdbcCurrencyServiceTest.java
git commit -m "feat(audit): record reference data mutations"
```

### Task 2: Protect exchange-rate history in PostgreSQL

**Files:**
- Create: `backend/src/main/resources/db/migration/V14__protect_exchange_rate_history.sql`
- Create: `backend/src/integrationTest/java/com/srm/creditengine/currency/ExchangeRateImmutabilityIntegrationTest.java`

**Interfaces:**
- Produces: PostgreSQL function `protect_exchange_rate_history()` and trigger `exchange_rates_immutable`.

- [ ] **Step 1: Add failing update/delete assertions**

Insert one valid exchange-rate row, then assert both statements fail and the original row remains unchanged:

```sql
update exchange_rates set rate = 9.99 where id = ?;
delete from exchange_rates where id = ?;
```

The test must run against PostgreSQL Testcontainers, not H2.

- [ ] **Step 2: Run the migration test red**

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend integrationTest --tests '*ExchangeRateImmutabilityIntegrationTest'
```

Expected: FAIL because PostgreSQL currently accepts the mutations.

- [ ] **Step 3: Add the immutable-history trigger**

Create:

```sql
create or replace function protect_exchange_rate_history()
returns trigger language plpgsql as $$
begin
  raise exception 'exchange_rates rows are immutable';
end;
$$;

create trigger exchange_rates_immutable
before update or delete on exchange_rates
for each row execute function protect_exchange_rate_history();
```

- [ ] **Step 4: Verify PostgreSQL enforcement**

Run the Step 2 command. Expected: PASS; failed mutations leave the row count and stored values unchanged.

- [ ] **Step 5: Commit the migration and proof**

```bash
git add -- backend/src/main/resources/db/migration/V14__protect_exchange_rate_history.sql backend/src/integrationTest/java/com/srm/creditengine/currency/ExchangeRateImmutabilityIntegrationTest.java
git commit -m "fix(currency): make FX observations append only"
```

### Task 3: Implement transient-only bounded exponential retry

**Files:**
- Modify: `backend/src/main/java/com/srm/creditengine/currency/application/FxRetryDelay.java`
- Modify: `backend/src/main/java/com/srm/creditengine/currency/application/HttpFxSynchronizationService.java`
- Modify: `backend/src/main/java/com/srm/creditengine/shared/runtime/FinancialTelemetry.java`
- Test: `backend/src/test/java/com/srm/creditengine/currency/application/HttpFxSynchronizationServiceTest.java`
- Test: `backend/src/test/java/com/srm/creditengine/shared/runtime/FinancialTelemetryTest.java`

**Interfaces:**
- Produces: `FxRetryDelay.exponential(DoubleSupplier jitter)`.
- Produces: timer `srm_fx_provider_attempt_duration_seconds{result=success|transient_failure|permanent_failure}`.

- [ ] **Step 1: Add failing policy tests**

Cover these exact outcomes:

```text
ResourceAccessException -> three attempts, delays 100ms then 200ms at jitter 1.0
HTTP 503 -> three attempts
HTTP 400 -> one attempt, no delay
invalid successful response body -> one attempt, no delay
open circuit -> zero HTTP attempts until exactly 30 seconds elapse
```

Assert all tags match `[A-Z_]{2,32}` and no pair, URL, actor, or payload becomes a tag.

- [ ] **Step 2: Run focused tests red**

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*HttpFxSynchronizationServiceTest' --tests '*FinancialTelemetryTest'
```

Expected: FAIL because every `RestClientException` is retried and delay is linear.

- [ ] **Step 3: Implement bounded exponential jitter**

Use:

```java
static FxRetryDelay exponential(DoubleSupplier jitter) {
    return failedAttempt -> {
        long exponentialMillis = Math.min(1_000L, 100L << (failedAttempt - 1));
        double factor = 0.5d + Math.max(0d, Math.min(1d, jitter.getAsDouble()));
        return Duration.ofMillis(Math.round(exponentialMillis * factor));
    };
}
```

Production injects `ThreadLocalRandom.current()::nextDouble`; tests inject `() -> 0.5d` for factor 1.0.

- [ ] **Step 4: Classify failures explicitly**

Implement:

```java
private static boolean retryable(RestClientException ex) {
    if (ex instanceof ResourceAccessException) return true;
    return ex instanceof RestClientResponseException response
            && response.getStatusCode().is5xxServerError();
}
```

A 4xx or invalid body records `permanent_failure`, opens no retry loop, and returns the controlled `FX_PROVIDER_UNAVAILABLE` response. Only exhausted transient failures open the 30-second circuit.

- [ ] **Step 5: Time every provider attempt**

Wrap each actual HTTP attempt with `Timer.Sample.start(registry)` and stop it on one of the three bounded result timers. Do not time a circuit-open short circuit as an HTTP attempt.

- [ ] **Step 6: Verify policy and telemetry**

Run the Step 2 command. Expected: PASS with exact attempt/delay counts.

- [ ] **Step 7: Commit resilience behavior**

```bash
git add -- backend/src/main/java/com/srm/creditengine/currency/application/FxRetryDelay.java backend/src/main/java/com/srm/creditengine/currency/application/HttpFxSynchronizationService.java backend/src/main/java/com/srm/creditengine/shared/runtime/FinancialTelemetry.java backend/src/test/java/com/srm/creditengine/currency/application/HttpFxSynchronizationServiceTest.java backend/src/test/java/com/srm/creditengine/shared/runtime/FinancialTelemetryTest.java
git commit -m "fix(currency): bound FX retries and latency metrics"
```

### Task 4: Complete authorization and authenticated request logging

**Files:**
- Modify: `backend/src/main/java/com/srm/creditengine/identity/api/SecurityConfiguration.java`
- Create: `backend/src/main/java/com/srm/creditengine/shared/runtime/AuthenticatedRequestLogFilter.java`
- Modify: `backend/src/main/java/com/srm/creditengine/shared/runtime/SafeOperationalLogger.java`
- Modify: `backend/src/main/java/com/srm/creditengine/shared/api/CorrelationIdFilter.java`
- Modify: `backend/src/main/java/com/srm/creditengine/identity/infrastructure/DevelopmentOperatorSeeder.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/java/com/srm/creditengine/shared/runtime/MockFxHealthIndicator.java`
- Modify: `compose.yaml`
- Test: `backend/src/test/java/com/srm/creditengine/identity/IdentityContractTest.java`
- Test: `backend/src/test/java/com/srm/creditengine/shared/api/CorrelationIdFilterTest.java`
- Test: `backend/src/test/java/com/srm/creditengine/api/RuntimeMetadataContractTest.java`
- Create: `backend/src/test/java/com/srm/creditengine/shared/runtime/SafeOperationalLoggerTest.java`

**Interfaces:**
- Produces: protected `GET /actuator/prometheus` requiring ADMIN.
- Produces: completion log fields limited to method, route class, status class, actor role, and correlation ID.

- [ ] **Step 1: Add failing matrix and logging tests**

Use parameterized tests for every permission-matrix cell, including Prometheus unauthenticated 401, OPERATOR 403, ADMIN 200. Add a filter-chain test asserting an authenticated ADMIN completion log contains `actor_role=ADMIN` but not email, token, request body, idempotency key, or path IDs.

- [ ] **Step 2: Run focused tests red**

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*IdentityContractTest' --tests '*CorrelationIdFilterTest'
```

- [ ] **Step 3: Add a post-authentication logging filter**

Create a `OncePerRequestFilter` that calls `SafeOperationalLogger.requestCompleted(request, response.getStatus())` inside `finally`, while `SecurityContextHolder` still contains bearer authentication. Extend `SafeOperationalLogger` to derive one bounded role or `ANONYMOUS`, status `2XX|4XX|5XX`, uppercase HTTP method, and the safe `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE` route template (for example `/api/v1/settlements/{settlementId}`); use `UNMATCHED` when the attribute is absent or fails `[/A-Za-z0-9_{}*.-]{1,160}`. Register the filter with:

```java
http.addFilterAfter(authenticatedRequestLogFilter, BearerTokenAuthenticationFilter.class);
```

Keep `CorrelationIdFilter` responsible only for validated/generate correlation ID and MDC lifecycle; remove any post-chain actor lookup from it.

- [ ] **Step 4: Protect Prometheus explicitly**

Before `.anyRequest().denyAll()` add:

```java
.requestMatchers(HttpMethod.GET, "/actuator/prometheus").hasRole("ADMIN")
```

Health remains public as currently documented.

- [ ] **Step 5: Seed a local reviewer ADMIN idempotently**

Extend the dev-only seeder with environment-backed `SRM_DEV_ADMIN_EMAIL` and `SRM_DEV_ADMIN_PASSWORD`. On every startup, reconcile the required role set in `user_roles` for existing dev accounts rather than returning after the first user insert. Add matching local-only Compose values without printing credentials.

- [ ] **Step 5A: Use one FX provider configuration property**

Make both `HttpFxSynchronizationService` and `MockFxHealthIndicator` read `srm.fx-provider.base-url`, backed by `SRM_MOCK_FX_BASE_URL`. Remove the alternate `srm.mock-fx-base-url` lookup. Add a runtime metadata test proving one configured URL drives both the service adapter and readiness indicator.

- [ ] **Step 6: Verify matrix and log safety**

Run the Step 2 command, `./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*RuntimeMetadataContractTest' --tests '*SafeOperationalLoggerTest'`, and `./scripts/tests/test_log_redaction.sh`. Expected: PASS.

- [ ] **Step 7: Commit security and logging changes**

```bash
git add -- backend/src/main/java/com/srm/creditengine/identity/api/SecurityConfiguration.java backend/src/main/java/com/srm/creditengine/shared/runtime/AuthenticatedRequestLogFilter.java backend/src/main/java/com/srm/creditengine/shared/runtime/SafeOperationalLogger.java backend/src/main/java/com/srm/creditengine/shared/runtime/MockFxHealthIndicator.java backend/src/main/java/com/srm/creditengine/shared/api/CorrelationIdFilter.java backend/src/main/java/com/srm/creditengine/identity/infrastructure/DevelopmentOperatorSeeder.java backend/src/main/resources/application.yml compose.yaml backend/src/test/java/com/srm/creditengine/identity/IdentityContractTest.java backend/src/test/java/com/srm/creditengine/shared/api/CorrelationIdFilterTest.java backend/src/test/java/com/srm/creditengine/shared/runtime/SafeOperationalLoggerTest.java backend/src/test/java/com/srm/creditengine/api/RuntimeMetadataContractTest.java
git commit -m "fix(security): complete authorization and actor logging"
```

### Task 5: Run the identity/currency lane

- [ ] **Step 1: Run focused unit tests**

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*Identity*' --tests '*Currency*' --tests '*ExchangeRate*' --tests '*Fx*' --tests '*ApiErrorContractTest' --tests '*CorrelationIdFilterTest' --tests '*FinancialTelemetryTest'
```

- [ ] **Step 2: Run PostgreSQL evidence**

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend integrationTest --tests '*PostgresMigrationIntegrationTest'
```

Expected: both commands pass with a working Testcontainers runtime.
