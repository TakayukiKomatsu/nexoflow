# SRM Pricing and Receivables Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide exact decimal transport, product-owned pricing behavior, complete immutable quote snapshots, and reproducible pricing vectors.

**Architecture:** Parse financial request values from textual JSON tokens into `BigDecimal`, keep authoritative calculation in the service, and return focused DTOs rather than persistence records. Each product Strategy selects its own risk-spread reference and executes its product pricing behavior; orchestration owns common timing, FX, and currency rounding.

**Tech Stack:** Java 21 records, Jackson, Jakarta Validation, Spring JDBC, PostgreSQL/Flyway, JUnit 5, Testcontainers.

## Global Constraints

- JSON numeric tokens for financial fields are rejected; accepted inputs and all outputs are decimal strings.
- Face amounts allow at most four fractional digits and must be positive.
- Currency settlement outputs use two fractional digits and `HALF_EVEN`.
- Pricing date is UTC from the injected application `Clock`.
- `ACTUAL_DAYS_30_MONTH` is exact days divided by 30 at scale 10 with `HALF_EVEN`.
- Quotes expire exactly at `expiresAt`; `now < expiresAt` is the only active condition.
- Existing Flyway migrations are not edited.

---

### Task 1: Enforce decimal-string HTTP inputs and outputs

**Files:**
- Create: `backend/src/main/java/com/srm/creditengine/shared/api/DecimalString.java`
- Modify: `backend/src/main/java/com/srm/creditengine/pricing/api/PricingController.java`
- Modify: `backend/src/main/java/com/srm/creditengine/receivable/api/ReceivableController.java`
- Test: `backend/src/test/java/com/srm/creditengine/pricing/api/PricingControllerTest.java`
- Create: `backend/src/test/java/com/srm/creditengine/receivable/api/ReceivableControllerTest.java`

**Interfaces:**
- Produces: `DecimalString.amount(int integerDigits, int fractionDigits)` is not required; use constructor validation at controller mapping.
- Produces: `DecimalString.value(): BigDecimal` and JSON string serialization through `@JsonValue`.

- [ ] **Step 1: Add failing JSON-shape tests**

For both pricing simulation and Receivable creation, assert:

```text
"faceAmount":"1000.25" -> accepted
"faceAmount":1000.25   -> 400 VALIDATION_FAILED or INVALID_REQUEST
"faceAmount":"0"      -> 400
"faceAmount":"1.00001"-> 400
```

Assert Receivable POST/GET/LIST responses contain `"faceAmount":"1000.2500"` or the agreed canonical four-place snapshot, never an unquoted JSON number.

- [ ] **Step 2: Run controller tests red**

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*PricingControllerTest' --tests '*ReceivableControllerTest'
```

- [ ] **Step 3: Implement strict textual decimal parsing**

Create:

```java
package com.srm.creditengine.shared.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

public record DecimalString(BigDecimal value) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static DecimalString from(JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw new IllegalArgumentException("Financial values must be decimal strings");
        }
        try {
            return new DecimalString(new BigDecimal(node.textValue()));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Financial values must be valid decimal strings");
        }
    }

    @JsonValue
    public String json() {
        return value.toPlainString();
    }
}
```

Controller mapping validates sign, integer precision, and scale before passing `value()` to application services. Do not use `double`, `Number`, or `parseFloat`.

- [ ] **Step 4: Return Receivable DTOs**

Replace direct `ReceivableService.Receivable` transport with:

```java
record Response(UUID id, UUID assignorId, String productType, String faceAmount,
                String faceCurrency, LocalDate issueDate, LocalDate dueDate,
                String status, long version) {
    static Response from(ReceivableService.Receivable value) {
        return new Response(value.id(), value.assignorId(), value.productType(),
                value.faceAmount().setScale(4, RoundingMode.HALF_EVEN).toPlainString(),
                value.faceCurrency(), value.issueDate(), value.dueDate(),
                value.status(), value.version());
    }
}
```

POST, GET, and LIST all use this DTO.

- [ ] **Step 5: Verify strict transport**

Run the Step 2 command. Expected: PASS with quoted decimal assertions.

- [ ] **Step 6: Commit transport contracts**

```bash
git add -- backend/src/main/java/com/srm/creditengine/shared/api/DecimalString.java backend/src/main/java/com/srm/creditengine/pricing/api/PricingController.java backend/src/main/java/com/srm/creditengine/receivable/api/ReceivableController.java backend/src/test/java/com/srm/creditengine/pricing/api/PricingControllerTest.java backend/src/test/java/com/srm/creditengine/receivable/api/ReceivableControllerTest.java
git commit -m "fix(api): enforce decimal-string financial transport"
```

### Task 2: Put risk-spread ownership behind the Strategy seam

**Files:**
- Modify: `backend/src/main/java/com/srm/creditengine/pricing/PricingStrategy.java`
- Modify: `backend/src/main/java/com/srm/creditengine/pricing/InvoicePricingStrategy.java`
- Modify: `backend/src/main/java/com/srm/creditengine/pricing/ChequePricingStrategy.java`
- Modify: `backend/src/main/java/com/srm/creditengine/pricing/application/AuthoritativePricingService.java`
- Test: `backend/src/test/java/com/srm/creditengine/pricing/application/PricingStrategyRegistryTest.java`
- Test: `backend/src/test/java/com/srm/creditengine/pricing/application/AuthoritativePricingServiceTest.java`

**Interfaces:**
- Produces: `PricingStrategy.riskSpread(ReferenceRateService, Instant): ReferenceRateService.ProductSpread`.
- Retains: `PricingStrategy.discount(BigDecimal, BigDecimal, BigDecimal, BigDecimal): BigDecimal`.

- [ ] **Step 1: Add failing ownership tests**

Mock `ReferenceRateService` and invoke each Strategy directly. Assert Invoice requests only `MERCANTILE_INVOICE`, Cheque requests only `POST_DATED_CHEQUE`, and the returned spread feeds that Strategy's discount calculation. Add an orchestration test that fails if `AuthoritativePricingService` calls `productSpreads(input.productType(), at)` itself.

- [ ] **Step 2: Run pricing tests red**

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*PricingStrategyRegistryTest' --tests '*AuthoritativePricingServiceTest'
```

- [ ] **Step 3: Extend the Strategy contract**

Add:

```java
ReferenceRateService.ProductSpread riskSpread(ReferenceRateService references, Instant at);
```

Implement Invoice with:

```java
return references.productSpreads("MERCANTILE_INVOICE", at).stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No effective invoice risk spread"));
```

Implement Cheque with the corresponding `POST_DATED_CHEQUE` key and safe message.

- [ ] **Step 4: Remove product branching from orchestration**

In `calculate`, resolve:

```java
PricingStrategy strategy = strategies.forProduct(input.productType());
BigDecimal base = effectiveBaseRate(input.faceCurrency(), at);
BigDecimal spread = strategy.riskSpread(references, at).monthlySpread();
BigDecimal discounted = strategy.discount(input.faceAmount(), base, spread, term);
```

Define the helper used above:

```java
private BigDecimal effectiveBaseRate(String currency, Instant at) {
    return references.baseRates(currency, at).stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No effective base rate"))
            .monthlyRate();
}
```

No `switch`, conditional, or reference lookup keyed by raw product input remains in `AuthoritativePricingService`.

- [ ] **Step 5: Verify product ownership**

Run the Step 2 command. Expected: PASS and independent spread calls for each product.

- [ ] **Step 6: Commit the deepened Strategy seam**

```bash
git add -- backend/src/main/java/com/srm/creditengine/pricing backend/src/main/java/com/srm/creditengine/pricing/application/AuthoritativePricingService.java backend/src/test/java/com/srm/creditengine/pricing/application/PricingStrategyRegistryTest.java backend/src/test/java/com/srm/creditengine/pricing/application/AuthoritativePricingServiceTest.java
git commit -m "refactor(pricing): move risk behavior into strategies"
```

### Task 3: Complete and normalize immutable Quote snapshots

**Files:**
- Create: `backend/src/main/resources/db/migration/V15__complete_pricing_quote_snapshots.sql`
- Modify: `backend/src/main/java/com/srm/creditengine/pricing/application/PricingService.java`
- Modify: `backend/src/main/java/com/srm/creditengine/pricing/application/AuthoritativePricingService.java`
- Modify: `backend/src/main/java/com/srm/creditengine/pricing/api/PricingController.java`
- Modify: `docs/architecture/schema-inventory.md` only after runtime proof
- Test: `backend/src/test/java/com/srm/creditengine/pricing/application/PricingQuoteSnapshotTest.java`
- Create: `backend/src/integrationTest/java/com/srm/creditengine/pricing/PricingQuotePostgresIntegrationTest.java`

**Interfaces:**
- Produces: `PricingService.Quote(UUID id, UUID receivableId, String productType, LocalDate dueDate, Breakdown breakdown, Instant expiresAt, String status, String createdBy)`.
- Produces: quote JSON fields `productType` and `dueDate` beside `pricing`.

- [ ] **Step 1: Add failing exact-roundtrip tests**

Assert a created quote and a subsequent GET have byte-equivalent financial strings, including `"settlementAmount":"975.61"`; both expose product type and due date. At `clock.instant() == expiresAt`, GET returns status `EXPIRED`; one nanosecond before returns `ACTIVE`.

The PostgreSQL test attempts to update every snapshot column and delete the quote, while separately proving the allowed `ACTIVE -> CONSUMED` status transition remains possible.

- [ ] **Step 2: Run snapshot tests red**

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*PricingQuoteSnapshotTest'
./scripts/with-java21.sh ./backend/gradlew -p backend integrationTest --tests '*PricingQuotePostgresIntegrationTest'
```

- [ ] **Step 3: Add the missing product snapshot**

Create:

```sql
alter table pricing_quotes
  add column product_type_code varchar(50);

update pricing_quotes q
set product_type_code = r.product_type_code
from receivables r
where r.id = q.receivable_id;

alter table pricing_quotes
  alter column product_type_code set not null,
  add constraint pricing_quotes_product_type_fk
    foreign key (product_type_code) references product_types(code);
```

Existing due date is retained and exposed; no historical snapshot is reconstructed at read time after this migration.

- [ ] **Step 4: Persist and rehydrate the complete quote**

Include `product_type_code` in INSERT and SELECT. Construct `Breakdown` money values with explicit scales:

```java
BigDecimal face = rs.getBigDecimal("face_amount").setScale(4, HALF_EVEN);
BigDecimal discounted = rs.getBigDecimal("discounted_amount").setScale(4, HALF_EVEN);
BigDecimal settlement = rs.getBigDecimal("settlement_amount").setScale(2, HALF_EVEN);
```

Return product type and due date from stored quote columns. Do not join the mutable Receivable on GET.

- [ ] **Step 5: Expose canonical Quote DTO fields**

Use:

```java
record QuoteResponse(UUID id, UUID receivableId, String productType,
                     LocalDate dueDate, Response pricing, Instant expiresAt,
                     String status, String createdBy) {}
```

All `Response` decimals use `toPlainString()` after the service's explicit scale normalization.

- [ ] **Step 6: Verify quote immutability and roundtrip**

Run the Step 2 commands. Expected: PASS against unit and PostgreSQL tests.

- [ ] **Step 7: Commit quote snapshots**

```bash
git add -- backend/src/main/resources/db/migration/V15__complete_pricing_quote_snapshots.sql backend/src/main/java/com/srm/creditengine/pricing/application/PricingService.java backend/src/main/java/com/srm/creditengine/pricing/application/AuthoritativePricingService.java backend/src/main/java/com/srm/creditengine/pricing/api/PricingController.java backend/src/test/java/com/srm/creditengine/pricing/application/PricingQuoteSnapshotTest.java backend/src/integrationTest/java/com/srm/creditengine/pricing/PricingQuotePostgresIntegrationTest.java
git commit -m "fix(pricing): complete immutable quote snapshots"
```

### Task 4: Add independent exact pricing vectors

**Files:**
- Create: `backend/src/test/java/com/srm/creditengine/pricing/application/PricingExactVectorTest.java`
- Modify: `backend/src/test/java/com/srm/creditengine/pricing/application/AuthoritativePricingServiceTest.java`

**Interfaces:**
- Consumes: stabilized Strategy and Quote contracts from Tasks 2–3.

- [ ] **Step 1: Define vectors with hard-coded expected decimals**

Cover all cases without deriving expected values through production helpers:

```text
MERCANTILE_INVOICE, same BRL, 30 days
POST_DATED_CHEQUE, same BRL, 45 days (fractional 1.5 months)
BRL -> USD direct FX
USD -> BRL inverse FX
HALF_EVEN tie at settlement currency scale
expiry at 14:59.999, 15:00.000, and 15:00.001 from pricedAt
```

Use literal expected `BigDecimal` strings calculated independently and documented in each test name/comment.

- [ ] **Step 2: Run the new vectors red, then correct only production defects**

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*PricingExactVectorTest' --tests '*AuthoritativePricingServiceTest'
```

Expected before any remaining correction: at least the cheque/direct/inverse/expiry coverage is new. Do not change expected literals to match incorrect production output.

- [ ] **Step 3: Verify the full pricing lane**

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*MoneyTest' --tests '*RateTest' --tests '*Pricing*' --tests '*Receivable*'
./scripts/with-java21.sh ./backend/gradlew -p backend integrationTest --tests '*PricingQuotePostgresIntegrationTest'
```

Expected: PASS.

- [ ] **Step 4: Commit exact-vector evidence**

```bash
git add -- backend/src/test/java/com/srm/creditengine/pricing/application/PricingExactVectorTest.java backend/src/test/java/com/srm/creditengine/pricing/application/AuthoritativePricingServiceTest.java
git commit -m "test(pricing): prove exact product and FX vectors"
```
