# SRM Settlement and Reporting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete strict Settlement APIs, immutable Reversal history, deterministic concurrency evidence, unique ledger movement identity, and PostgreSQL-backed reporting contracts.

**Architecture:** Keep settlement work in one database transaction and use PostgreSQL row/unique constraints as the concurrency authority. Derive stable collision-free ledger IDs for each signed movement in the SQL read model, validate paging without integer overflow, and test the complete path against PostgreSQL rather than mocks.

**Tech Stack:** Spring MVC, Jackson, Spring JDBC transactions, PostgreSQL/Flyway, JUnit 5, Testcontainers.

## Global Constraints

- Preview is non-persisted and non-reserving.
- Settlement and Reversal are never automatically retried.
- Idempotency is scoped by actor, operation, and key; a different request hash is a 409 conflict.
- Ordered Quote IDs are part of the request hash.
- Settlement is all-or-nothing.
- Reversal is whole, terminal, append-only, and never reopens a Receivable.
- Ledger signed amounts are positive for Settlement and negative for Reversal.
- Reporting uses `[from,to)` and one parameterized SQL query per page.

---

### Task 1: Make request parsing and idempotency errors strict

**Files:**
- Create: `backend/src/main/java/com/srm/creditengine/shared/api/JacksonConfiguration.java`
- Modify: `backend/src/main/java/com/srm/creditengine/shared/api/ApiExceptionHandler.java`
- Create: `backend/src/main/java/com/srm/creditengine/settlement/application/PricingQuoteExpiredException.java`
- Modify: `backend/src/main/java/com/srm/creditengine/settlement/application/JdbcSettlementService.java`
- Test: `backend/src/test/java/com/srm/creditengine/settlement/api/SettlementControllerTest.java`
- Test: `backend/src/test/java/com/srm/creditengine/api/ApiErrorContractTest.java`

**Interfaces:**
- Consumes: currency exceptions and mapping handoff from Identity/Currency Task 1.
- Produces: global Jackson `FAIL_ON_UNKNOWN_PROPERTIES=true`.
- Produces: codes `UNSUPPORTED_CURRENCY`, `FX_RATE_MISSING`, `FX_RATE_STALE`, `IDEMPOTENCY_KEY_REQUIRED`, and `PRICING_QUOTE_EXPIRED`.

- [ ] **Step 1: Add failing API tests**

Assert both Settlement and Reversal reject:

```text
missing Idempotency-Key -> 400 IDEMPOTENCY_KEY_REQUIRED
blank Idempotency-Key -> 400 VALIDATION_FAILED
201-character key -> 400 VALIDATION_FAILED
unknown fields totalAmount, actor, status -> 400
expired quote -> 409 PRICING_QUOTE_EXPIRED
```

Keep `IDEMPOTENCY_KEY_REUSED`, `ALREADY_SETTLED`, and `ALREADY_REVERSED` as distinct 409 codes.

- [ ] **Step 2: Run API tests red**

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*SettlementControllerTest' --tests '*ApiErrorContractTest'
```

- [ ] **Step 3: Reject unknown JSON fields globally**

Create:

```java
@Configuration
class JacksonConfiguration {
    @Bean
    Jackson2ObjectMapperBuilderCustomizer strictRequestJson() {
        return builder -> builder.featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
```

Add a focused handler for `HttpMessageNotReadableException` that returns 400 `VALIDATION_FAILED` with a safe request-level violation; never echo the unknown value or raw body.

- [ ] **Step 4: Map missing headers separately**

Handle `MissingRequestHeaderException` before generic validation:

```java
@ExceptionHandler(MissingRequestHeaderException.class)
ProblemDetail missingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
    String code = "Idempotency-Key".equalsIgnoreCase(ex.getHeaderName())
            ? "IDEMPOTENCY_KEY_REQUIRED" : "VALIDATION_FAILED";
    return problem(HttpStatus.BAD_REQUEST, code, "Idempotency-Key header is required.", request);
}
```

Only use the idempotency message for that exact header.

- [ ] **Step 4A: Integrate the shared currency mappings**

As the sole `ApiExceptionHandler.java` owner, add the Identity/Currency handoff exactly:

```text
UnsupportedCurrencyException -> 400 UNSUPPORTED_CURRENCY
FxRateMissingException       -> 422 FX_RATE_MISSING
FxRateStaleException         -> 422 FX_RATE_STALE
```

Add exact code/status/content-type assertions to `ApiErrorContractTest`. Wait for the Identity/Currency owner to create the exception classes before compiling this step; other Settlement tasks may proceed independently.

- [ ] **Step 5: Replace generic expiry failure**

At quote validation:

```java
if (!"ACTIVE".equals(quote.quoteStatus()) || !now.isBefore(quote.expiresAt())) {
    throw new PricingQuoteExpiredException();
}
```

Map it to 409 `PRICING_QUOTE_EXPIRED` without quote IDs.

- [ ] **Step 6: Verify strict API behavior**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 7: Commit strict request handling**

```bash
git add -- backend/src/main/java/com/srm/creditengine/shared/api/JacksonConfiguration.java backend/src/main/java/com/srm/creditengine/shared/api/ApiExceptionHandler.java backend/src/main/java/com/srm/creditengine/settlement/application/PricingQuoteExpiredException.java backend/src/main/java/com/srm/creditengine/settlement/application/JdbcSettlementService.java backend/src/test/java/com/srm/creditengine/settlement/api/SettlementControllerTest.java backend/src/test/java/com/srm/creditengine/api/ApiErrorContractTest.java
git commit -m "fix(settlement): enforce strict request contracts"
```

### Task 2: Protect Reversal history in PostgreSQL

**Files:**
- Create: `backend/src/main/resources/db/migration/V16__protect_settlement_reversal_history.sql`
- Create: `backend/src/integrationTest/java/com/srm/creditengine/settlement/SettlementReversalImmutabilityIntegrationTest.java`

**Interfaces:**
- Produces: function `protect_settlement_reversal_history()` and trigger `settlement_reversals_immutable`.

- [ ] **Step 1: Add failing mutation tests**

Create a real Settlement and Reversal, then assert PostgreSQL rejects:

```sql
update settlement_reversals set reason = 'rewritten' where id = ?;
delete from settlement_reversals where id = ?;
```

Verify the original reason, actor, timestamp, and row count are unchanged.

- [ ] **Step 2: Run PostgreSQL test red**

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend integrationTest --tests '*SettlementReversalImmutabilityIntegrationTest'
```

- [ ] **Step 3: Add the immutable trigger**

```sql
create or replace function protect_settlement_reversal_history()
returns trigger language plpgsql as $$
begin
  raise exception 'settlement_reversals rows are immutable';
end;
$$;

create trigger settlement_reversals_immutable
before update or delete on settlement_reversals
for each row execute function protect_settlement_reversal_history();
```

- [ ] **Step 4: Verify the trigger**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit Reversal immutability**

```bash
git add -- backend/src/main/resources/db/migration/V16__protect_settlement_reversal_history.sql backend/src/integrationTest/java/com/srm/creditengine/settlement/SettlementReversalImmutabilityIntegrationTest.java
git commit -m "fix(settlement): make reversals append only"
```

### Task 3: Make concurrency and rollback tests deterministic and isolated

**Files:**
- Modify: `backend/src/integrationTest/java/com/srm/creditengine/settlement/SettlementAtomicityIntegrationTest.java`
- Modify: `backend/src/integrationTest/java/com/srm/creditengine/settlement/application/JdbcSettlementServiceConcurrencyIntegrationTest.java`

**Interfaces:**
- Consumes: existing production transaction and idempotency implementation unchanged unless a test exposes a real defect.

- [ ] **Step 1: Isolate every scenario's rows**

Before each assertion, capture the generated Assignor, Receivable, Quote, idempotency key, and Settlement IDs. Assert only rows reachable from those IDs. Do not assert global table counts such as `count(*) = 0` or depend on method order.

Use cleanup only for test-created rows and respect FK order; alternatively wrap fixture creation/assertion in a transaction that rolls back after each non-concurrent test.

- [ ] **Step 2: Synchronize same-key concurrency at the database claim**

In the test schema, install a test-only trigger on `idempotency_records` whose body takes a PostgreSQL advisory lock derived from the known test key. Acquire that advisory lock on a control connection before starting both service calls, wait until `pg_stat_activity` shows both calls reached the claim statement, then release the lock. This replaces sleeps and exercises the real production SQL.

- [ ] **Step 3: Assert all concurrency outcomes exactly**

Cover:

```text
same actor + same operation + same key + same ordered body -> one Settlement, both exact bodies equal, one replay header
same key + different ordered body -> one success and one IDEMPOTENCY_KEY_REUSED
same Receivable + different keys -> one success and one ALREADY_SETTLED
mid-transaction injected failure -> no Settlement, items, consumption, Receivable transition, completed idempotency, or audit event
```

For each test compare before/after counts scoped to generated IDs.

- [ ] **Step 4: Run the integration class repeatedly**

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend integrationTest --tests '*SettlementAtomicityIntegrationTest' --tests '*JdbcSettlementServiceConcurrencyIntegrationTest'
./scripts/with-java21.sh ./backend/gradlew -p backend integrationTest --tests '*SettlementAtomicityIntegrationTest' --tests '*JdbcSettlementServiceConcurrencyIntegrationTest'
```

Expected: both complete runs pass; no order dependence or arbitrary wait exists.

- [ ] **Step 5: Commit deterministic PostgreSQL evidence**

```bash
git add -- backend/src/integrationTest/java/com/srm/creditengine/settlement/SettlementAtomicityIntegrationTest.java backend/src/integrationTest/java/com/srm/creditengine/settlement/application/JdbcSettlementServiceConcurrencyIntegrationTest.java
git commit -m "test(settlement): isolate atomic concurrency evidence"
```

### Task 4: Prove whole Reversal idempotency and terminal lifecycle

**Files:**
- Create: `backend/src/integrationTest/java/com/srm/creditengine/settlement/SettlementReversalIntegrationTest.java`
- Modify: `backend/src/main/java/com/srm/creditengine/settlement/application/JdbcSettlementService.java` only if the red test exposes a defect.

**Interfaces:**
- Consumes: `SettlementService.reverse(UUID, String, String, String)`.

- [ ] **Step 1: Add real PostgreSQL scenarios**

Prove:

```text
same actor/key/settlement/reason -> same reversal ID and exact body, replayed=true on second call
different key after reversal -> ALREADY_REVERSED
same key with different reason -> IDEMPOTENCY_KEY_REUSED
all Settlement Receivables become REVERSED atomically
no Receivable returns to REGISTERED
one negative movement exists per original item
```

- [ ] **Step 2: Run the new class red**

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend integrationTest --tests '*SettlementReversalIntegrationTest'
```

- [ ] **Step 3: Correct only observed production defects**

Preserve the existing transaction. A replay must load the stored Reversal, not reconstruct reason/time/actor from the current request. Any failed path leaves the idempotency record non-completed and no partial Reversal/lifecycle changes.

- [ ] **Step 4: Re-run the class**

Expected: PASS.

- [ ] **Step 5: Commit Reversal evidence**

```bash
git add -- backend/src/integrationTest/java/com/srm/creditengine/settlement/SettlementReversalIntegrationTest.java backend/src/main/java/com/srm/creditengine/settlement/application/JdbcSettlementService.java
git commit -m "test(settlement): prove terminal idempotent reversal"
```

### Task 5: Give each ledger movement a stable unique ID

**Files:**
- Modify: `backend/src/main/java/com/srm/creditengine/reporting/application/JdbcSettlementStatementService.java`
- Modify: `backend/src/main/java/com/srm/creditengine/reporting/api/SettlementStatementController.java`
- Test: `backend/src/test/java/com/srm/creditengine/reporting/api/SettlementStatementControllerTest.java`
- Create: `backend/src/integrationTest/java/com/srm/creditengine/reporting/SettlementStatementPostgresIntegrationTest.java`

**Interfaces:**
- Retains: `SettlementStatementService.Entry.entryId(): UUID`.
- Produces: deterministic distinct UUIDs for the positive and negative movement of one Settlement item.

- [ ] **Step 1: Add failing unique-ID and filter tests**

Create one reversed two-item Settlement. Assert four distinct `entryId` values, stable across repeated queries and pagination. Cover `[from,to)`, every filter alone and combined, ties in `effective_at`, page sizes 1/25/100, and an injection string such as `BRL' OR 1=1 --` returning no unintended rows.

- [ ] **Step 2: Run reporting tests red**

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*SettlementStatementControllerTest'
./scripts/with-java21.sh ./backend/gradlew -p backend integrationTest --tests '*SettlementStatementPostgresIntegrationTest'
```

- [ ] **Step 3: Derive distinct deterministic IDs in SQL**

Use PostgreSQL UUID casts from distinct namespaces:

```sql
md5('SETTLEMENT:' || i.id::text)::uuid as entry_id
md5('REVERSAL:' || r.id::text || ':' || i.id::text)::uuid as entry_id
```

Keep all filter values as JDBC parameters. Order by `effective_at desc, entry_id desc`.

- [ ] **Step 4: Prevent pagination overflow**

Calculate:

```java
long offset = Math.multiplyExact((long) filter.page(), (long) filter.size());
```

Reject an overflow or offset above the documented maximum with `IllegalArgumentException`; pass the `long` as the JDBC parameter.

- [ ] **Step 5: Prove one query per page**

Wrap the integration-test DataSource with a counting proxy or `JdbcTemplate` query interceptor. Reset the counter immediately before `query(filter)` and assert exactly one prepared query executes.

- [ ] **Step 6: Verify the reporting contract**

Run the Step 2 commands. Expected: PASS.

- [ ] **Step 7: Commit reporting correctness**

```bash
git add -- backend/src/main/java/com/srm/creditengine/reporting/application/JdbcSettlementStatementService.java backend/src/main/java/com/srm/creditengine/reporting/api/SettlementStatementController.java backend/src/test/java/com/srm/creditengine/reporting/api/SettlementStatementControllerTest.java backend/src/integrationTest/java/com/srm/creditengine/reporting/SettlementStatementPostgresIntegrationTest.java
git commit -m "fix(reporting): stabilize signed ledger identity"
```

### Task 6: Run the settlement/reporting lane

- [ ] **Step 1: Run focused unit tests**

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*Settlement*' --tests '*Reporting*' --tests '*Audit*' --tests '*ApiErrorContractTest'
```

- [ ] **Step 2: Run focused PostgreSQL tests**

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend integrationTest --tests '*Settlement*' --tests '*Statement*' --tests '*PostgresMigrationIntegrationTest'
```

Expected: all commands pass with no cross-test row leakage.
