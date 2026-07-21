# Validated Plan Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair the frontend quote-response contract, add the missing executable evidence, and make the documentation accurately prove the complete local senior/staff delivery boundary.

**Architecture:** The TypeScript client mirrors the backend `PricingController.QuoteResponse` without aliases. Focused tests exercise the real wire shape and controlled FX errors. A Java OpenAPI assertion plus a Node TypeScript-shape assertion make contract drift executable; a narrowly scoped AST guard protects the browser’s server-authoritative financial boundary. Documentation maps every required check to the verified command that executes it.

**Tech Stack:** Java 21, Spring Boot/OpenAPI, React 19, TypeScript 6, Vitest, Playwright, Node.js, Bash, Make, PostgreSQL/Flyway.

## Global Constraints

- Preserve Java 21, Spring Boot modular monolith, React/TypeScript SPA, PostgreSQL/Flyway authority, BRL/USD support, decimal-string transport, and `HALF_EVEN` financial rounding.
- No browser-side authoritative financial calculation, API compatibility aliases, generated-client dependency, test-only production bypass, fabricated evidence, production-scale claim, remote push, tag, PR, or release mutation.
- SDD 12 remote publication/release stays authorization-gated.
- Use deterministic fixtures and web-first synchronization; never add fixed sleeps.
- Each `Implemented` documentation claim must name a real source/test path and a command run in the final verification.

---

### Task 1: Model and render the exact Pricing Quote response

**Files:**
- Modify: `frontend/src/api/client.ts:7-35`
- Modify: `frontend/src/App.tsx:651-667`
- Modify: `frontend/src/App.test.tsx:20-39,71-77,430-530`
- Test: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: backend `PricingController.QuoteResponse(UUID id, UUID receivableId, String productType, LocalDate dueDate, Response pricing, Instant expiresAt, String status, String createdBy)`.
- Produces: `PricingQuote` with top-level `productType: string`, `dueDate: string`, and nested `pricing: PricingBreakdown`.

- [ ] **Step 1: Write the failing wire-shape regression test**

Replace the quote fixture with a response that has metadata only at the top level, then assert the rendered quote card:

```ts
const quote = {
  id: "quote-1",
  receivableId: "receivable-1",
  productType: "MERCANTILE_INVOICE",
  dueDate: "2030-02-14",
  pricing: pricing("966.18"),
  expiresAt: "2030-01-15T12:05:00Z",
  status: "ACTIVE",
};

expect(article).toHaveTextContent("Product type");
expect(article).toHaveTextContent("MERCANTILE_INVOICE");
expect(article).toHaveTextContent("Due date");
expect(article).toHaveTextContent("2030-02-14");
```

- [ ] **Step 2: Run the focused test and observe the current defect**

Run: `npm --prefix frontend test -- --run App.test.tsx`

Expected: FAIL because the card reads `quote.pricing.productType` and `quote.pricing.dueDate`.

- [ ] **Step 3: Split quote metadata from simulation input in the client**

Replace the existing quote type with this structure:

```ts
export type PricingBreakdown = Omit<PricingSimulation, "productType" | "dueDate">;

export type PricingQuote = {
  id: string;
  receivableId: string;
  productType: string;
  dueDate: string;
  pricing: PricingBreakdown;
  expiresAt: string;
  status: string;
};
```

Keep `PricingSimulation` unchanged for `/pricing-simulations`, because that endpoint receives and returns the input metadata. Update the fixture helper so `pricing()` returns only the backend `Response` fields.

- [ ] **Step 4: Render quote metadata from its actual response location**

Change only the two incorrect accesses:

```tsx
<dd>{quote.productType}</dd>
...
<dd>{quote.dueDate}</dd>
```

- [ ] **Step 5: Run focused tests and typecheck**

Run: `npm --prefix frontend test -- --run App.test.tsx && npm --prefix frontend run typecheck`

Expected: all App tests pass and TypeScript reports no errors.

- [ ] **Step 6: Commit the contract repair**

```bash
git add frontend/src/api/client.ts frontend/src/App.tsx frontend/src/App.test.tsx
git commit -m "fix(frontend): align quote response contract"
```

### Task 2: Add deterministic FX-state and authority-boundary proof

**Files:**
- Modify: `frontend/src/App.test.tsx`
- Create: `frontend/scripts/validate-authoritative-pricing.mjs`
- Modify: `frontend/package.json:6-20`
- Modify: `Makefile:1-100`
- Test: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: `PricingSimulation` server response and RFC 9457 `ApiError` handling in `App`.
- Produces: `npm --prefix frontend run validate:authoritative-pricing` and `make validate-frontend-authority`.

- [ ] **Step 1: Add cross-currency and stale-FX UI tests**

Add one simulation fixture with real server-returned USD/BRL metadata and one controlled Problem Detail response:

```ts
const crossCurrencySimulation = {
  ...simulation("193.24"),
  faceCurrency: "BRL",
  settlementCurrency: "USD",
  fxBaseCurrency: "BRL",
  fxQuoteCurrency: "USD",
  fxRate: "0.2000000000",
  fxSource: "MANUAL",
  settlementAmount: "193.24",
};

const staleFx = response(
  { status: 409, code: "FX_RATE_STALE", detail: "Selected FX rate is stale." },
  409,
);
```

Assert the simulation region renders `BRL/USD`, `0.2000000000`, and `MANUAL`, and that the stale response renders the server detail in the focused `alert`.

- [ ] **Step 2: Run the new tests and establish the existing controlled-error baseline**

Run: `npm --prefix frontend test -- --run App.test.tsx`

Expected: PASS. These tests close missing proof paths for existing server-authoritative behavior; they must not require a new browser calculation or an error-specific production branch.

- [ ] **Step 3: Add the smallest test-only fixture support needed**

Use the existing `stubFetch`, `response`, fake timers, and `signIn` helpers. Do not add a frontend formula, fallback quote value, or mock-specific branch to `App.tsx`; the existing `ApiError` message path is the production behavior under test.
- [ ] **Step 4: Create the AST-backed authority validator**


Create `frontend/scripts/validate-authoritative-pricing.mjs`. Use the installed `typescript` compiler API to parse `src/App.tsx` and `src/api/client.ts`. For every arithmetic `BinaryExpression` (`+`, `-`, `*`, `/`, `%`, `**`), reject it only when either operand subtree accesses a financial field from this set:

```js
const financialFields = new Set([
  "faceAmount", "baseRate", "spread", "termInMonths",
  "discountedAmount", "fxRate", "settlementAmount",
]);
```

Print `AUTHORITY-001 passed: no browser-side financial arithmetic` on success. On violation, print filename, line, column, and expression then exit 1. Date/session/request-counter arithmetic remains legal because it does not access a listed field.

- [ ] **Step 5: Wire the validator into package and Make targets**

Add:

```json
"validate:authoritative-pricing": "node scripts/validate-authoritative-pricing.mjs"
```

and:

```make
validate-frontend-authority:
	npm --prefix frontend run validate:authoritative-pricing
```

Include the target in `.PHONY` and `release-check`. It is a runtime code-boundary validation, so it does not belong in the documentation-only `validate-docs` target.

- [ ] **Step 6: Run focused proof**

Run: `npm --prefix frontend test -- --run App.test.tsx && npm --prefix frontend run validate:authoritative-pricing`

Expected: UI tests pass; `AUTHORITY-001 passed: no browser-side financial arithmetic`.

- [ ] **Step 7: Commit the frontend authority proof**

```bash
git add frontend/src/App.test.tsx frontend/scripts/validate-authoritative-pricing.mjs frontend/package.json Makefile
git commit -m "test(frontend): prove authoritative pricing boundary"
```

### Task 3: Make backend OpenAPI and frontend quote expectations jointly executable

**Files:**
- Modify: `backend/src/test/java/com/srm/creditengine/api/RuntimeMetadataContractTest.java`
- Create: `frontend/scripts/validate-pricing-quote-contract.mjs`
- Modify: `frontend/package.json`
- Modify: `Makefile`
- Test: `backend/src/test/java/com/srm/creditengine/api/RuntimeMetadataContractTest.java`

**Interfaces:**
- Consumes: generated `/v3/api-docs` JSON from the Spring runtime and `frontend/src/api/client.ts` source.
- Produces: `make validate-frontend-api-contract`, which passes only when API schema and client shape both expose top-level quote metadata and nested breakdown fields.

- [ ] **Step 1: Write the backend OpenAPI schema assertion**

Extend `RuntimeMetadataContractTest` to fetch `/v3/api-docs`, navigate to `components.schemas.QuoteResponse.properties`, and assert these fields exist:

```java
assertThat(quoteProperties).containsKeys(
    "id", "receivableId", "productType", "dueDate",
    "pricing", "expiresAt", "status", "createdBy");
assertThat(quoteProperties.get("pricing").get("$ref").asText())
    .endsWith("/Response");
```

Then assert `Response.properties` contains `faceAmount`, `faceCurrency`, `settlementCurrency`, `baseRate`, `spread`, `strategyCode`, `dayCountConvention`, `termInMonths`, `discountedAmount`, `fxBaseCurrency`, `fxQuoteCurrency`, `fxRate`, `fxSource`, `fxObservedAt`, `settlementAmount`, and `pricedAt`, but does not contain `productType` or `dueDate`.

- [ ] **Step 2: Run the backend contract test**

Run: `./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*RuntimeMetadataContractTest'`

Expected: PASS after the assertion documents the already-generated contract.

- [ ] **Step 3: Create the TypeScript client-shape assertion**

Create `frontend/scripts/validate-pricing-quote-contract.mjs`. Read `src/api/client.ts`; fail unless it contains all of these exact declarations:

```js
const requiredQuoteFields = [
  "id: string;", "receivableId: string;", "productType: string;",
  "dueDate: string;", "pricing: PricingBreakdown;", "expiresAt: string;",
  "status: string;",
];
const forbiddenBreakdownFields = ["productType", "dueDate"];
```

Extract the `PricingBreakdown` declaration block and fail if it contains either forbidden field. Print `API-CONTRACT-001 passed: frontend quote shape matches OpenAPI quote boundary` on success.

- [ ] **Step 4: Wire one explicit contract command**

Add this frontend script:

```json
"validate:pricing-quote-contract": "node scripts/validate-pricing-quote-contract.mjs"
```

Add this Make target:

```make
validate-frontend-api-contract:
	./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*RuntimeMetadataContractTest'
	npm --prefix frontend run validate:pricing-quote-contract
```

Add it to `.PHONY` and `release-check`; do not add it to `validate-docs`, which remains focused on documentation integrity.

- [ ] **Step 5: Run the cross-boundary proof**

Run: `make validate-frontend-api-contract`

Expected: Java OpenAPI assertions pass, then `API-CONTRACT-001 passed: frontend quote shape matches OpenAPI quote boundary`.

- [ ] **Step 6: Commit the executable API guard**

```bash
git add backend/src/test/java/com/srm/creditengine/api/RuntimeMetadataContractTest.java frontend/scripts/validate-pricing-quote-contract.mjs frontend/package.json Makefile
git commit -m "test: validate frontend quote OpenAPI contract"
```

### Task 4: Extend browser evidence and retain all failure artifacts

**Files:**
- Modify: `frontend/e2e/operator-critical-path.spec.ts:71-87`
- Modify: `frontend/playwright.config.ts:3-24`
- Test: `frontend/e2e/operator-critical-path.spec.ts`
**Interfaces:**
- Consumes: the real Compose-backed `E2E-001` workflow and quote breakdown article.
- Produces: browser proof of quote metadata plus trace/screenshot/video retained on every failure.

- [ ] **Step 1: Write browser assertions for returned quote metadata**

After the quote article is visible, add:

```ts
await expect(quoteArticle.getByText("Product type")).toBeVisible();
await expect(quoteArticle.getByText("MERCANTILE_INVOICE")).toBeVisible();
await expect(quoteArticle.getByText("Due date")).toBeVisible();
await expect(quoteArticle.getByText(DUE_DATE)).toBeVisible();
```

- [ ] **Step 2: Configure artifacts for every failed attempt**

Replace the `use` values with:

```ts
trace: "retain-on-failure",
screenshot: "only-on-failure",
video: "retain-on-failure",
```

Keep CI retries and zero local retries unchanged; `retain-on-failure` is what makes the first local failure traceable.

- [ ] **Step 3: Run the deterministic browser path**

Run: `make test-ui-features`

Expected: `E2E-001` passes against Compose; no `waitForTimeout` is introduced. If Docker is unavailable, report the exact command as blocked and continue with all non-container gates.

- [ ] **Step 4: Commit browser evidence changes**

```bash
git add frontend/e2e/operator-critical-path.spec.ts frontend/playwright.config.ts
git commit -m "test(e2e): prove quote metadata and retain failures"
```

### Task 5: Reconcile traceability and reporting claims with executable truth

**Files:**
- Modify: `docs/REQUIREMENT_TRACEABILITY.md:9-55`
- Modify: `docs/SRM_REQUIREMENTS_PLAN.md:345-385`
- Modify: `docs/sdd/07_sdd_frontend-authentication-and-mandatory-live-simulation.md:90-94`
- Modify: `docs/sdd/11_sdd_staff-artifacts-and-reviewer-documentation.md:67-81`
- Modify: `scripts/validate-traceability.sh:13-86`
- Test: `scripts/validate-traceability.sh`

**Interfaces:**
- Consumes: new `AUTHORITY-001` and `API-CONTRACT-001` checks; existing DOCS and E2E commands.
- Produces: one stable matrix and one supplementary-proof matrix where every declared check names an existing source and executable command.

- [ ] **Step 1: Add failing traceability assertions for documentation IDs**

Extend `validate-traceability.sh` with an explicit required list after SDD scenario parsing:

```bash
required_check_ids=(
  DOC-LINK-001 DOC-SCHEMA-002 DOC-TRACE-003 DOC-MONEY-004 DOC-CLAIM-005
  AUTHORITY-001 API-CONTRACT-001
)
```

Validate each ID has exactly one traceability row, an existing backtick-delimited artifact path, and an executable command, using the same rules as stable SDD scenarios.

- [ ] **Step 2: Run the traceability checker and observe missing rows**

Run: `make validate-traceability`

Expected: FAIL listing the absent `DOC-*`, `AUTHORITY-001`, and `API-CONTRACT-001` rows.

- [ ] **Step 3: Add accurate evidence rows and supplementary mappings**

In `REQUIREMENT_TRACEABILITY.md`:

- Add rows for each `DOC-*` ID pointing to its existing test/script and `make validate-docs` or `make validate-traceability`.
- Add `AUTHORITY-001` pointing to `frontend/scripts/validate-authoritative-pricing.mjs` and `make validate-frontend-authority`.
- Add `API-CONTRACT-001` pointing to `frontend/scripts/validate-pricing-quote-contract.mjs` and `make validate-frontend-api-contract`.
- For `UI-SIM-002`, `UI-SIM-005`, `UI-SETTLE-004`, and `UI-LEDGER-006`, name both their unit-test source and `frontend/e2e/operator-critical-path.spec.ts`, with `make test-ui-features` as browser proof.
- Add a supplementary SDD 04–10 matrix that maps the previously omitted pricing vectors, settlement conflict/replay/stale/duplicate/rollback cases, reporting boundaries/filter/role/page/no-N+1 checks, observability redaction/cardinality, deterministic fixtures, security scans, and browser evidence to their existing exact test/script paths and commands.

- [ ] **Step 4: Correct the reporting claim**

Replace:

```md
- Query plans use the intended indexes on representative data.
```

with:

```md
- Parameterized, indexable reporting filters and stable server-side pagination are exercised on representative PostgreSQL data; PostgreSQL may select sequential scans when its cost model estimates them cheaper.
```

Ensure the matrix calls `docs/evidence/reporting-explain.txt` representative query-shape evidence, not guaranteed index-use evidence.

- [ ] **Step 5: Repair literal SDD 07 verification and evidence text**

Replace its zero-match focused command with the real command:

```text
npm --prefix frontend test -- --run App.test.tsx && npm --prefix frontend run test:a11y && make validate-frontend-api-contract && make validate-frontend-authority
```

State that the Playwright `E2E-001` path supplies the browser smoke for UI simulation/settlement/ledger scenarios.

- [ ] **Step 6: Run documentation gates**

Run: `make validate-docs && make validate-traceability`

Expected: both pass, including each explicit supplemental ID.

- [ ] **Step 7: Commit truthful evidence mapping**

```bash
git add docs/REQUIREMENT_TRACEABILITY.md docs/SRM_REQUIREMENTS_PLAN.md docs/sdd/07_sdd_frontend-authentication-and-mandatory-live-simulation.md scripts/validate-traceability.sh
git commit -m "docs: reconcile plan evidence with executable checks"
```

### Task 6: Verify the full local senior/staff boundary

**Files:**
- Evidence: generated reports only after their corresponding command passes; no verification-only source changes are planned.

**Interfaces:**
- Consumes: all implementation and documentation changes from Tasks 1–5.
- Produces: fresh verification evidence without remote mutation.

- [ ] **Step 1: Run focused non-container gates**

Run:

```bash
npm --prefix frontend run typecheck
npm --prefix frontend run build
npm --prefix frontend run validate:authoritative-pricing
npm --prefix frontend run validate:pricing-quote-contract
make validate-frontend-api-contract
make validate-docs
make validate-traceability
make test-coverage
```

Expected: all pass. `make test-coverage` must report the backend risk gate and frontend coverage above the configured thresholds.

- [ ] **Step 2: Run the senior/staff operational and acceptance gates**

Run:

```bash
make verify-fast
make test-log-redaction
make test-api-features
make test-ui-features
make explain-statements-representative
make security-scan
make test-crisis-evidence
```

Expected: every locally supported command passes. Docker-dependent commands are reported as blocked only when `docker info` fails; do not label blocked output as passing evidence.

- [ ] **Step 3: Inspect original-brief coverage before final claim**

Confirm current passing evidence exists for: FX updates/mock resilience; Strategy/decimal/cross-currency pricing; ACID/idempotent/concurrent settlement; REST/OpenAPI; SQL statement filters/pagination; frontend simulation/grid/errors; Docker runtime; hooks/CI; observability; C4/ADR/ER/DDL; GitHub Flow/crisis evidence; proposed 1M TPM/EDA evolution; AI-use disclosure.

- [ ] **Step 4: Run final release-equivalent check when Docker is available**

Run: `make release-check`

Expected: all locally authorized quality, security, runtime, E2E, evidence, and documentation gates pass. Do not create tags, push, publish, or mutate remotes.

- [ ] **Step 5: Preserve a clean verification result**

Generated reports remain untracked evidence. Do not create a verification-only commit. If a command finds a defect, return to the owning task, add a concrete corrective step and test there, then rerun this task from Step 1.
