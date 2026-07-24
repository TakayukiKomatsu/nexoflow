# SRM Credit Engine — Corrected 12-Prompt SDD Suite

This suite is executed from the **repository root**, in numeric order. Source requirements are [`docs/README_case_dev_srm.md`](./README_case_dev_srm.md) and [`docs/SRM_REQUIREMENTS_PLAN.md`](./SRM_REQUIREMENTS_PLAN.md). It targets a senior-quality modular monolith plus selected staff artifacts in 3–4 days; it does not claim production scale.

## Global execution contract

This contract applies to Prompts 01–12 and is not optional.

### Baseline, scope, and dependencies

1. Read `docs/README_case_dev_srm.md`, `docs/SRM_REQUIREMENTS_PLAN.md`, `docs/CONTEXT.md`, this contract, the current prompt, existing ADRs, migrations, OpenAPI, feature files, and prior prompt evidence. Use the canonical domain terms from `docs/CONTEXT.md`; resolve a new or conflicting domain term there immediately, not in implementation code or an ADR.
2. Inspect `git status --short` and the staged index before and after the increment. Preserve unrelated changes; never blanket-stage. Handoff with an empty staged index.
3. Run the named baseline/regression command before editing. Stop on an unexpected failure or an unmet prerequisite. Never skip, disable, weaken, or silently rewrite a test to get green.
4. Implement only the prompt scope. Keep optional IaC, real microservices, a real IdP/market provider, refresh tokens, dashboards, and actual 1M-transactions/minute infrastructure out of scope.
5. Use Java 21, Spring Boot 3, PostgreSQL/Flyway, React/TypeScript, and the repository-selected pinned tool versions. Controllers contain no business logic; application Modules own transactions; domain code avoids framework coupling; SQL read models may bypass domain reconstruction.

### Module design and domain language

- A **Module** presents one small **Interface** at an explicit **Seam**. Its Interface includes its invariants, ordering, error modes, configuration, and performance characteristics—not just types or public methods.
- Prefer deep Modules: callers obtain substantial behavior through a small Interface. Hide orchestration, persistence mechanics, retries, locking, rounding mechanics, and provider protocols in the Module's Implementation. Do not create shallow pass-through Modules.
- Introduce an Adapter at a Seam only where behavior genuinely varies now (for example, a mock versus HTTP FX provider, or PostgreSQL versus an in-memory test Adapter). One Adapter alone is a hypothetical Seam and must be justified.
- Callers and tests cross the same Interface. Accept dependencies at internal Seams rather than constructing them in domain logic; return results rather than leaking side effects. Apply the deletion test: deleting a Module should force its hidden complexity back into multiple callers.
- When a prompt resolves business language, update `docs/CONTEXT.md` inline using the domain-model format. Create or amend an ADR only for a hard-to-reverse, surprising trade-off with real alternatives; ADRs must not become a domain glossary.

### Executable specification and TDD

- Every business/API feature is specified in Gherkin and executed with Cucumber-JUnit against Spring and PostgreSQL Testcontainers. The critical browser feature is executed with Playwright BDD or an equally direct feature-to-test adapter.
- Every feature and scenario has a stable ID (for example `PRICE-001`) visible in the test report. Every step names a real role/actor, fixed fixture, concrete request/action, and exact observable response, row/state change, event, log, or metric. Tautologies and prose-only assertions are forbidden.
- Infrastructure/documentation contracts use executable shell, schema, OpenAPI, Mermaid, and link tests rather than artificial Gherkin; externally observable infrastructure behavior may use Gherkin.
- For each coherent behavior: **RED** — add the smallest meaningful test and capture the expected failure; **GREEN** — implement only enough and run the focused plus relevant regression suites; **REFACTOR** — improve design while green. Record RED/GREEN commands and concise output.
- Use unit tests for financial/domain boundaries, Cucumber/API tests for contracts, Testcontainers for migrations/transactions/authorization, deterministic barrier-based PostgreSQL concurrency tests, component tests for UI state, and Playwright for the critical path. No sleeps in concurrency/E2E synchronization.
- Exact financial values travel as decimal strings. Java authoritative code uses `BigDecimal`, never `float`/`double`; JavaScript never calculates authoritative financial outcomes. Use `NUMERIC(19,4)` for stored money, `NUMERIC(19,10)` for rates/intermediates where specified, `HALF_EVEN` at currency boundaries, and an injectable `Clock` for all time-dependent behavior.

### Git, security, and authorization boundaries

- Work on the prompt’s named focused branch, never directly on `main`. Use GitHub Flow, Conventional Commits, protected-main review/check expectations, and rebase-before-merge only while the branch is unpublished.
- Commit migrations, persistence, production code, and tests together when they form one indivisible green contract. Make local atomic commits only when the focused and regression gates are green. The isolated crisis exercise in Prompt 12 is the sole, tightly scoped red-commit exception.
- Local branch creation and local commits are allowed. Do **not** push, create/merge a PR, rebase a shared branch, force-push, change repository visibility, publish evidence/release, or create/push a tag without the explicit authorization gate in Prompt 12. Never fabricate URLs, checks, reviews, or remote evidence.
- Never expose credentials, JWTs, idempotency keys, personal data, secrets, or unrestricted financial payloads in logs/evidence. Scan fixtures as well as source. A fake-secret test fixture must be unmistakably non-live and allowlisted only by exact path/reason if required.

### Required increment handoff

An increment is accepted only with: stable scenario report IDs; RED and GREEN output; focused and regression commands/results; changed files; migrations/contracts affected; local commit hashes/messages; security or query evidence requested by the prompt; residual risks/deferrals; `git status --short`; and proof that the staged index is empty. If a command is unavailable or an authorization gate is closed, report `blocked`, never `passed`.

## Timebox, dependencies, and review gates

- **Day 1:** Prompts 01–03. **Day 2:** 04–06. **Day 3:** 07–10. **Day 4:** 11–12 and review buffer.
- Strict chain: `01 → 02 → 03 → 04 → 05 → 06`; Prompt 07 depends on 03–06; Prompt 08 depends on 05–07; Prompt 09 depends on 02–08; Prompt 10 depends on all implementation prompts; Prompt 11 reconciles 01 with implemented truth; Prompt 12 requires every prior gate green.
- Review gates: foundation 01–02; core backend 03–04; integrity/ledger 05–06; operator UI 07–08; operations/release readiness 09–11; publication 12. Never cross a red financial-integrity or security gate.
- Prefer 5–6 reviewable PRs at authorized collaboration time: foundation, core backend, integrity/ledger, operator UI, operations/evidence, documentation/release. Do not create 12 trivial PRs or one unreviewable dump.

---

## Prompt 01 — Governed repository and architecture foundation

**Objective:** Establish the monorepo, branch policy, hooks, CI, ADRs, and ER contract before feature code.

**Prerequisites:** Repository root is available; requirements documents have been read; no earlier SDD prompt exists. Start local branch `feature/foundation` without touching `main`.

**Scope:**

- Scaffold Java 21/Spring Boot backend and strict React/TypeScript/Vite frontend; add root `Makefile`, formatting/lint/test commands, `.editorconfig`, `.gitignore`, and safe environment example.
- Document GitHub Flow, branch naming, Conventional Commits, protected-main required review/checks, unpublished rebase-before-merge, no force-push, and emergency hook-bypass accountability.
- Add fast pre-commit format/lint/staged-secret checks, commit-msg validation, and pre-push unit tests. Each hook must have a directly invocable CI script.
- Add least-privilege CI with pinned actions: backend/frontend lint, typecheck, unit/integration gates, migration/Compose validation, Gitleaks and dependency scans. Missing suites fail rather than silently skip. Reserve documented CodeQL/Trivy jobs for completion in Prompt 10.
- Create PR template, ownership/reviewer placeholder only when accurate, and requirement→feature traceability skeleton.
- Create `docs/CONTEXT.md` as the canonical SRM domain glossary; it must define Assignor, Receivable, Product Type, Pricing Simulation, Pricing Quote, Settlement Preview, Settlement, Settlement Reversal, Settlement Ledger Entry, Asset Currency, Settlement Currency, Base Rate, Risk Spread, Pricing Term, and Exchange-Rate Pair without implementation details. Create early ADRs for modular monolith, PostgreSQL, JPA writes/SQL reads, decimals/rounding, local JWT→OIDC, immutable quote snapshots, and atomic idempotency. Add Mermaid ER source for every planned table and FK/unique/version constraint.

**Non-goals:** Business endpoints, financial calculations, database migrations, remote PRs, publication, and tags.

**Repository contracts:** Planned Modules are `identity`, `assignor`, `receivable`, `currency`, `pricing`, `settlement`, `reporting`, `audit`, `shared`; their Implementations may organize code into `api/application/domain/infrastructure`, but that package shape is not an Interface. `docs/CONTEXT.md` is the sole glossary. CI permissions are read-only unless a documented job requires more.

**Module design:** Document each planned Module's caller-facing Interface, primary invariant, and why its Seam exists. Do not expose repository, framework, or provider details through another Module's Interface. Add one architecture test that rejects an import from a domain Module to a framework or persistence Adapter.

**Executable acceptance:**

```gherkin
Feature: REPO-GIT governed local history
  Scenario: FIN-GIT-001 malformed commit message is blocked locally
    Given a disposable Git repository with this project's hooks installed
    And file "note.txt" containing "safe fixture" is staged
    And HEAD is recorded as H0
    When developer Ana attempts commit message "updates"
    Then the commit command exits non-zero
    And stderr contains "Conventional Commit"
    And HEAD remains H0

  Scenario: FIN-GIT-002 staged fake credential is blocked
    Given a disposable Git repository with this project's hooks installed
    And "fixture.env" contains the scanner's documented non-live canary
    When developer Ana invokes the pre-commit script against "fixture.env"
    Then the script exits non-zero
    And output identifies "fixture.env"
    And output does not print the complete canary value
```

**Test mapping:** `FIN-GIT-001/002` → shell acceptance tests in a disposable repository. ADR/ER links → link checker, Mermaid renderer, and ER-schema consistency script. CI YAML → action/workflow linter. Architecture boundaries → ArchUnit negative fixture test.

**Verification:** Focused: `make test-hooks && make validate-architecture-docs`. Regression: `make verify-fast && make validate-workflows`; also run `docker compose config` once Compose skeleton exists.

**Evidence:** Hook exit/output excerpts, rendered Mermaid artifact, ADR/link/schema report, workflow permission/pinning report, changed files, and required global handoff.

**Commit outcomes:** Separate green commits: `chore(repo): establish governed engineering foundation`; `docs(architecture): record initial decisions and ER model`; `ci: add early quality and security gates`.

---

## Prompt 02 — Full-stack runtime, API conventions, and deterministic fixtures

**Objective:** Provide a clean-checkout, full-stack Compose runtime and stable persistence/API test foundation.

**Prerequisites:** Prompt 01 gate is green; ADR/ER contracts are reviewed; hook and CI scripts work. Start local branch `feature/runtime`.

**Scope:**

- Default Compose includes PostgreSQL, backend, built frontend/web server, and deterministic mock FX service; add health checks, dependency conditions, internal network, named volume, environment examples, and non-root/read-only settings where practical.
- Configure Flyway as sole schema authority, strict ORM schema validation, PostgreSQL Testcontainers, and migration smoke tests.
- Implement `/api/v1` convention, RFC 9457 `application/problem+json` with stable code/correlation/violations, correlation propagation, OpenAPI, liveness, and dependency-aware readiness.
- Add idempotent one-shot `dev-fixtures` and `e2e-fixtures` profiles. E2E uses fixed clock `2030-01-15T12:00:00Z`, fixed IDs/dates/rates. Dev fixtures use relative/non-expiring-safe data so wall-clock passage does not stale them. Reset exists only database-side/profile-side and never in production API/config.
- Add a shell smoke test that enters the actual Compose network and verifies frontend→API and API→database/mock FX connectivity.

**Non-goals:** Identity/domain tables, pricing behavior, production credentials, or optional monitoring UI (Prometheus profile may remain deferred).

**API/runtime contract:** Unexpected errors expose no class/SQL/stack/secret; validation lists fields. Liveness excludes dependencies; readiness includes PostgreSQL. Default stack starts with `docker compose up --build --wait`.

**Executable acceptance:**

```gherkin
Feature: OPS-RUN reproducible runtime
  Scenario: OPS-RUN-001 readiness follows PostgreSQL availability
    Given reviewer Rui started the default Compose stack and every service is healthy
    When Rui stops the PostgreSQL service
    Then GET "/actuator/health/liveness" returns 200
    And GET "/actuator/health/readiness" eventually returns 503
    When Rui starts PostgreSQL and its health check passes
    Then GET "/actuator/health/readiness" eventually returns 200 without restarting the API

  Scenario: OPS-FIX-002 E2E fixture loading is idempotent
    Given an empty E2E database and fixture set "baseline-v1"
    When reviewer Rui runs the e2e-fixtures job twice
    Then both jobs exit 0
    And each fixed fixture ID occurs exactly once
    And the second database checksum equals the first
```

**Test mapping:** `OPS-RUN-001` → Compose smoke/Cucumber HTTP test; `OPS-FIX-002` → profile shell + PostgreSQL assertions. Problem details → REST Assured contract matrix for 400/404/409/415/500. Migration behavior → Testcontainers.

**Verification:** Focused: `make test-runtime && ./backend/gradlew -p backend integrationTest`. Regression: `docker compose config && docker compose up --build --wait && make smoke-compose && docker compose down -v && make verify-fast`.

**Evidence:** Compose service/health output, fixture checksums/counts, migration report, OpenAPI/problem contract report, production-profile search proving absence of reset/seeded password, and global handoff.

**Commit outcomes:** `chore(runtime): add reproducible full-stack Compose`; `feat(api): establish migration and problem-details contracts`.

---

## Prompt 03 — Identity, authorization, reference rates, and resilient FX

**Objective:** Secure APIs deny-by-default and provide auditable, versioned pricing reference data and FX integration.

**Prerequisites:** Prompts 01–02 green; Testcontainers/fixtures/error contract available. Work on `feature/identity-currency`.

**Scope:**

- Migrate users/roles; hash passwords; issue signed 15-minute JWTs; generic login failure; boundary rate limiting; `POST /api/v1/auth/login`, `GET /api/v1/users/me`.
- Define `OPERATOR`, `ANALYST`, `ADMIN`, `AUDITOR`, application-facing actor context, permission matrix, endpoint and method guards, and deny-by-default unmapped API behavior.
- Migrate currencies/products/versioned product spreads/base rates and immutable FX observations. Seed BRL/USD, invoice `0.015`, cheque `0.025`; store rates as decimal fractions.
- Add ADMIN create/list APIs, deterministic effective-at selection, `BASE/QUOTE` semantics, direct multiply/inverse divide/identity, exact 24-hour stale boundary, and no triangulation.
- Add deterministic mock HTTP provider port, manual ADMIN sync, timeouts, bounded transient retry/jitter/circuit breaker, controlled errors/metrics. Never apply provider or generic retry to settlements.
- Local/E2E credentials only through environment; redact passwords/tokens/secrets from logs and audit metadata.

**Module design:** `Identity` owns credential verification and Current Actor resolution behind a small Interface used by other Modules; its Spring Security Adapter stays internal. `Currency` owns effective-rate selection, staleness, pair direction, and provider resilience behind `resolveConversion`/`synchronizeRates` Interfaces. The FX-provider Seam is real because deterministic mock and HTTP Adapters vary; rate repositories remain internal to Currency. Neither Module exposes JWT parsing, HTTP client, or persistence types.

**Non-goals:** OIDC, refresh tokens, real provider, receivables, quote persistence, and settlement.

**Contract:** OPERATOR writes receivables/quotes/settlements later; ANALYST reads financial records; ADMIN manages references/users/reversal; AUDITOR reads financial/audit records. Historical rate rows are immutable. `1 BASE = rate QUOTE`.

**Executable acceptance:**

```gherkin
Feature: SEC-FX secured reference data
  Scenario: AUTH-003 operator cannot mutate an FX rate
    Given user "operator@srm.local" has role OPERATOR and a valid token expiring at 2030-01-15T12:15:00Z
    When that user POSTs USD/BRL rate "5.20" observed at "2030-01-15T11:00:00Z" to "/api/v1/exchange-rates"
    Then the response is 403 application/problem+json with code "ACCESS_DENIED"
    And the exchange_rates row count is unchanged

  Scenario: FX-004 latest non-stale direct rate is selected
    Given application clock "2030-01-15T12:00:00Z"
    And USD/BRL observations are "5.10" at "2030-01-14T11:59:59Z" and "5.20" at "2030-01-15T11:00:00Z"
    When authenticated OPERATOR "operator@srm.local" requests conversion of USD "100.00" to BRL
    Then observation "5.20" is selected
    And unroundedConvertedAmount is "520.0000000000"
    And settlementAmount is "520.00"

  Scenario: FX-RES-006 transient provider failures are bounded
    Given mock FX returns 503 twice and then USD/BRL "5.20"
    When ADMIN "admin@srm.local" triggers one provider sync
    Then exactly 3 provider requests occur
    And one immutable exchange_rates row with rate "5.20" is stored
    And no credential appears in captured logs
```

**Test mapping:** `AUTH-003` → Cucumber/REST Assured + database assertion; auth matrix includes anonymous/malformed/expired token 401 and each role 403/allowed cases. `FX-004` → unit + Cucumber exact decimal and 24h-minus/at/plus boundary matrix. `FX-RES-006` → controllable HTTP stub and resilience metrics.

**Verification:** Focused: `./backend/gradlew -p backend test --tests '*Identity*' --tests '*Currency*' integrationTest`. Regression: `make verify-backend && make test-api-features`.

**Evidence:** Authorization matrix report, scenario IDs, exact boundary vectors, retry request count/circuit state, log-redaction scan, migrations/OpenAPI diff, and global handoff.

**Commit outcomes:** `feat(identity): secure local authentication and authorization`; `feat(currency): add versioned rates and resilient FX`.

---

## Prompt 04 — Assignors, receivables, authoritative simulation, and quotes

**Objective:** Implement exact strategy pricing and mandatory server-authoritative simulation feeding immutable quotes.

**Prerequisites:** Prompts 01–03 green; clock, actor, rates, products, FX, error/OpenAPI contracts available. Work on `feature/receivables-pricing`.

**Scope:**

- Migrate assignors/receivables with normalized unique tax ID, active status, exact face money, dates/status/version; expose authorized create/read/list APIs.
- Implement Money/Rate/CurrencyCode, `ACTUAL_DAYS_30_MONTH`, justified high-precision fractional power, `HALF_EVEN`, and Strategy registry for invoice/cheque without product switches in orchestration.
- Add non-persisting `POST /api/v1/pricing-simulations`; accept decimal strings and reference/input IDs, use server time and common engine, and return exact breakdown.
- Add immutable 15-minute `POST/GET /api/v1/pricing-quotes` for persisted REGISTERED receivables; snapshot base/spread/strategy/day-count/FX/input/output/actor/time. Client cannot supply authoritative fields.
- Cover same/direct/inverse FX, stale/missing rates, product differences, fractional terms, expiry boundaries, exact serialization, and reproducibility after reference data changes.

**Module design:** `Receivable` owns registration invariants and lifecycle behind commands/results, never mutable persistence entities. `Pricing` is a deep Module whose small Interface accepts a pricing input and returns a breakdown or domain error; it hides day-count arithmetic, decimal exponentiation, Strategy selection, effective-rate lookup, and FX direction. Product risk is a real internal Strategy Seam because two Product Types vary. Pricing Quote persistence is internal to Pricing; callers never compose rate/spread/FX calculations.

**Non-goals:** Simulations as reservations/audit quotes/settlement inputs, JavaScript calculation, batch settlement, or mutable quote snapshots.

**API/schema contract:** Pricing formula is `face / (1 + base + spread)^(days/30)`, then FX. Quote status derives as ACTIVE/EXPIRED/CONSUMED; `Clock` controls boundary. Due date is after pricing date. Output includes all reproduction inputs and exact decimal strings.

**Executable acceptance:**

```gherkin
Feature: PRICE authoritative pricing
  Scenario: PRICE-001 server simulates an invoice without persisting a quote
    Given clock "2030-01-15T12:00:00Z", BRL base rate "0.010", and invoice spread "0.015"
    And OPERATOR "operator@srm.local" supplies face BRL "1000.00" and due date "2030-02-14"
    When the operator POSTs those inputs to "/api/v1/pricing-simulations" for BRL settlement
    Then the response is 200
    And termInMonths is "1.0000000000"
    And settlementAmount is "975.61"
    And the pricing_quotes row count is unchanged

  Scenario: QUOTE-005 quote snapshots survive reference changes
    Given receivable "00000000-0000-0000-0000-000000000401" is REGISTERED for BRL "1000.00"
    When OPERATOR "operator@srm.local" creates a quote at "2030-01-15T12:00:00Z"
    And ADMIN later creates invoice spread "0.020" effective "2030-01-15T12:01:00Z"
    Then GET of the quote still returns spread "0.015" and settlementAmount "975.61"
    And expiresAt is "2030-01-15T12:15:00Z"
```

**Test mapping:** `PRICE-001` → financial unit vector + Cucumber API/database count. `QUOTE-005` → PostgreSQL snapshot test. Add IDs for cheque expected value, fractional term independent vectors, same/direct/inverse FX, and expiry at `14:59.999`, `15:00.000`.

**Verification:** Focused: `./backend/gradlew -p backend test --tests '*Pricing*' --tests '*Receivable*' integrationTest`. Regression: `make verify-backend && make test-api-features`.

**Evidence:** Independent calculation source/vectors, scenario report, strategy registry architecture test, quote row snapshot, OpenAPI/migration diff, RED/GREEN output, and global handoff.

**Commit outcomes:** `feat(receivable): register assignors and receivables`; `feat(pricing): add authoritative simulation and auditable quotes`.

---

## Prompt 05 — Settlement preview and atomic idempotent settlement

**Objective:** Deliver preview, idempotency, concurrency, and all-or-nothing settlement as one coherent financial contract.

**Prerequisites:** Prompt 04 quotes green and unmodified; PostgreSQL integration/concurrency harness available. Work on `feature/atomic-settlement`.

**Scope:**

- `POST /api/v1/settlement-previews` accepts ordered unique quote IDs, revalidates active/unexpired quotes, REGISTERED receivables, one assignor/currency, and returns ordered snapshots, authoritative total, `asOf`, earliest expiry. It persists nothing, locks/reserves nothing, and may become stale.
- Migrate settlements/items/idempotency records. `POST /api/v1/settlements` accepts quote IDs only and a required bounded `Idempotency-Key`; schema rejects totals/status/actor/calculated fields.
- Atomically claim scoped `(actor/client, operation, key)` plus canonical request hash, serialize concurrent claimers, create settlement/items, consume quotes, settle receivables, and complete idempotency. Thrown failure rolls all back including claim.
- Same key/hash replays immutable ID/status/body (only replay header may differ); same key/different hash → `409 IDEMPOTENCY_KEY_REUSED`; validation/auth 4xx and rolled-back 5xx are not cached.
- Different keys racing for one receivable: one `201`, one `409 ALREADY_SETTLED`. Unique item-by-receivable and optimistic versions are final safeguards. No retry wraps settlement.

**Module design:** `Settlement` is a deep Module with two caller-facing Interfaces: `preview(orderedQuoteIds, actor)` and `settle(orderedQuoteIds, idempotencyKey, actor)`. It owns validation, canonicalization, totals, idempotency, optimistic locking, and the ACID transition internally; no caller sees lock, repository, transaction, or idempotency-record mechanics. Its only external dependency is the already-issued Pricing Quote interface; no hypothetical Adapter Seam is introduced for persistence.

**Non-goals:** Reservation, client totals, partial settlement, asynchronous processing, reversal, or auto-retry.

**Transaction contract:** Preview and create share validation policy but create always revalidates. The ordered quote list defines canonical payload. Completed records are immutable. Fault injection exists only in test configuration.

**Executable acceptance:**

```gherkin
Feature: SETTLE atomic settlement
  Scenario: SETTLE-006 concurrent replay produces one atomic result
    Given active BRL quotes "q501" and "q502" for one assignor total "1900.00"
    And two OPERATOR requests use key "e2e-settle-006" and ordered IDs ["q501","q502"]
    When separate PostgreSQL transactions submit both requests at barrier "after-key-claim"
    Then both responses identify the same settlement ID
    And exactly one settlement, two items, and one COMPLETED idempotency row exist
    And both quotes are CONSUMED and both receivables are SETTLED

  Scenario: SETTLE-ROLLBACK-008 injected failure leaves no partial state
    Given active quote "q508" and no settlement for its receivable
    When OPERATOR "operator@srm.local" settles with key "rollback-008" and test fault "after-settlement-insert"
    Then the response is controlled 500 with a correlation ID
    And zero settlement, item, and idempotency rows exist for the request
    And quote "q508" is ACTIVE and its receivable is REGISTERED
```

**Test mapping:** Preview contract → Cucumber + row counts. `SETTLE-006/008` → real PostgreSQL, separate transactions, latches/barriers, no sleeps. Add different-key race, key/hash conflict, same-key sequential replay, stale-after-preview, duplicate IDs, rollback at each mutation point, and no-retry configuration tests.

**Verification:** Focused: `./backend/gradlew -p backend settlementTest integrationTest --tests '*Settlement*'`. Regression: `make verify-backend && make test-api-features`.

**Evidence:** Barrier trace, row/state counts, replay response comparison, fault-injection rollback output, transaction/isolation note, constraints, scenario report, and global handoff.

**Commit outcomes:** `feat(settlement): add authoritative batch preview`; then one indivisible green commit `feat(settlement): create batches atomically and idempotently` containing migration, implementation, rollback, idempotency, and concurrency proof.

---

## Prompt 06 — Whole-settlement reversal, append-only audit, and ledger

**Objective:** Define terminal correction semantics and an explicit compensating-entry statement ledger.

**Prerequisites:** Prompt 05 integrity gate green. Work on `feature/reversal-ledger`; do not weaken settlement invariants.

**Scope:**

- ADMIN-only whole-settlement reversal; required reason and idempotency key; one immutable reversal. Original settlement/items/quotes remain unchanged; affected receivables become terminal `REVERSED` and can never settle again. Correction requires a new receivable/quote.
- Same reversal key/hash replays original; a different key after reversal → `409 ALREADY_REVERSED`. Reversal, receivable transitions, idempotency outcome, and audit event commit atomically.
- Append-only audit events capture actor/action/target/time/correlation/safe metadata for reference mutation, quote, settlement, reversal. Audit detail is ADMIN/AUDITOR.
- `GET /api/v1/settlement-statements` is a ledger: one positive SETTLEMENT entry/item and one negative REVERSAL entry/reversed item; signed amount, `effectiveAt`, original settlement ID, optional reversal ID, immutable item dimensions. Default includes both types.
- `[from,to)` UTC filters apply to entry `effectiveAt`; `assignorId`, `assetCurrency`, `settlementCurrency`, and `productType` filters combine with the period; deterministic `effectiveAt DESC, entryId DESC`; bounded server pagination; parameterized optimized SQL; no N+1. OPERATOR/ANALYST/ADMIN/AUDITOR have role-wide statement read because no tenant ownership model exists.

**Module design:** Settlement Reversal extends the Settlement Module through one explicit `reverse(settlementId, reason, idempotencyKey, actor)` Interface; it hides compensation, terminal lifecycle transition, and audit append mechanics. `Reporting` is a read-only deep Module with one ledger-query Interface; its SQL Adapter owns joins, filters, ordering, pagination, and query shape. The reporting SQL Adapter is a real distinct path because report reads deliberately bypass aggregate reconstruction.

**Non-goals:** Partial reversal, restoring REGISTERED, deleting history, net-only status, ownership inferred from query parameters, or resettling reversed receivables.

**API/schema contract:** Reversal endpoint uses its own scoped idempotency operation. Ledger preserves both movements; it never overwrites a positive entry with current net state.

**Executable acceptance:**

```gherkin
Feature: REPORT-REV compensating ledger
  Scenario: REPORT-REV-003 reversal remains visible as a compensating entry
    Given settlement "S1" has one BRL item "975.61" effective "2030-01-15T12:05:00Z"
    And ADMIN "admin@srm.local" reverses S1 at "2030-01-16T09:00:00Z" for "duplicate source document"
    When AUDITOR "auditor@srm.local" requests entries from "2030-01-15T00:00:00Z" to "2030-01-17T00:00:00Z"
    Then the ledger contains a SETTLEMENT entry amount "975.61"
    And a REVERSAL entry amount "-975.61" linked to S1
    And S1 and its item snapshot bytes are unchanged
    And the receivable status is REVERSED

  Scenario: REVERSE-007 repeated reversal is terminal and idempotent
    Given ADMIN reversed settlement "S7" using key "reverse-007"
    When the same ADMIN repeats the same payload and key
    Then the API returns the original reversal ID
    When the ADMIN repeats it using key "reverse-007-new"
    Then the API returns 409 with code "ALREADY_REVERSED"
```

**Test mapping:** `REPORT-REV-003/REVERSE-007` → Cucumber + PostgreSQL immutable hashes/rows. Add fault rollback, `[from,to)` exact boundary, each filter/role, stable pagination tie, injection attempt, query-count, and representative `EXPLAIN` index tests.

**Verification:** Focused: `./backend/gradlew -p backend integrationTest --tests '*Reversal*' --tests '*Statement*'`. Regression: `make verify-backend && make test-api-features && make explain-statements`.

**Evidence:** Before/after hashes, signed ledger response, rollback counts, EXPLAIN plan/index usage and dataset size, authorization matrix, audit redaction result, and global handoff.

**Commit outcomes:** `feat(settlement): add atomic whole-settlement reversals`; `feat(reporting): expose the immutable settlement ledger`.

---

## Prompt 07 — Frontend authentication and mandatory live simulation

**Objective:** Build an accessible operator workflow whose real-time result always comes from the server.

**Prerequisites:** Prompts 03–06 API/OpenAPI green; Prompt 05/06 contracts frozen. Work on `feature/operator-pricing-ui`.

**Scope:**

- Generate or maintain a typed OpenAPI-aligned client; implement login/session, protected routes, token-expiry handling, permission-aware actions, receivable form, and quote breakdown.
- On every valid pricing-field change, debounce exactly one `/pricing-simulations` request; cancel or ignore superseded responses; show loading/stale/validation/FX/server states; render returned decimal strings verbatim.
- Creating a receivable and quote remains explicit. Typing must not create quote/settlement, and JavaScript must contain no authoritative pricing formula.
- Test same/cross currency, stale FX, invalid input, 401/403, slow-old/fast-new response ordering, keyboard flow, labels/focus/error announcements.

**Module design:** `Operator Workflow` is a frontend Module with a small Interface expressed by route state and user actions; it owns debounce, cancellation, stale-response suppression, validation presentation, and server-state invalidation. Its typed transport Adapter is internal. The frontend crosses backend Module Interfaces through generated contracts and renders results; it does not reimplement pricing or settlement behavior.

**Non-goals:** Local/offline pricing, optimistic financial amounts, settlement confirmation/grid, or UI-authoritative permissions.

**UI contract:** Debounce duration is documented and tested with fake timers. Query identity includes all pricing inputs. A stale response never replaces the latest request state.

**Executable acceptance:**

```gherkin
Feature: UI-SIM server-authoritative live pricing
  Scenario: UI-SIM-002 product change refreshes only the server result
    Given OPERATOR "operator@srm.local" is on the receivable form with valid invoice inputs
    And the displayed server simulation amount is "975.61"
    When the operator selects product "POST_DATED_CHEQUE"
    Then exactly one debounced POST to "/api/v1/pricing-simulations" is sent after inputs settle
    And the UI eventually displays returned amount "966.18"
    And no pricing quote or settlement request is sent

  Scenario: UI-SIM-005 an old response cannot overwrite a new response
    Given request A for invoice is pending and request B for cheque returns "966.18"
    When request A later returns "975.61"
    Then the displayed amount remains "966.18"
    And the visible product remains "POST_DATED_CHEQUE"
```

**Test mapping:** `UI-SIM-002/005` → React Testing Library + MSW/fake timers and Playwright BDD browser smoke. Static test rejects known formula/arithmetic in authoritative state path. Accessibility → axe plus keyboard assertions. Auth expiry → component/network tests.

**Verification:** Focused: `npm --prefix frontend test -- --run pricing auth && npm --prefix frontend run test:a11y`. Regression: `npm --prefix frontend run lint && npm --prefix frontend run typecheck && npm --prefix frontend test -- --run && npm --prefix frontend run build && make test-ui-features`.

**Evidence:** Request log/count/timestamps, stale-response trace, DOM exact values, accessibility report, OpenAPI client diff/check, no-local-formula result, and global handoff.

**Commit outcome:** `feat(frontend): add authenticated real-time pricing workflow`.

---

## Prompt 08 — Frontend preview, settlement intent, and reversal ledger

**Objective:** Confirm only fresh server previews, preserve retry intent, and expose signed settlement/reversal history.

**Prerequisites:** Prompts 05–07 green; preview/settlement/ledger OpenAPI contracts available. Work on `feature/operator-settlement-ui`.

**Scope:**

- Add quote selection and require a fresh server preview before confirmation. Render server items/total/expiry verbatim; never authoritatively sum in JavaScript.
- Generate one idempotency key for each explicit confirmation intent; retain across timeout/lost/unknown outcomes and Retry; replace only after cancellation or a new intent. Suppress double submit.
- Handle stale preview, expired quote, key conflict, concurrent settlement, replay, 401/403, and actionable unknown outcome. Settlement request contains quote IDs only; no client total.
- Add URL-backed server-side ledger filters/pagination. Render SETTLEMENT and REVERSAL distinctly with signed values and links.

**Module design:** `Settlement Intent` is a frontend Module with one Interface: select Quotes, request a Settlement Preview, then confirm exactly that server-owned intent. It hides idempotency-key retention, retry state, and cache invalidation. `Settlement Ledger` is a separate read Module whose Interface is URL state plus rendered pages; it delegates filtering/pagination to Reporting rather than duplicating it in the browser.

**Non-goals:** Client reservation, client recomputation, reversal mutation UI (visibility is required), infinite unbounded grid, or generating a key per network retry.

**UI contract:** Any quote/input change invalidates preview. Confirmation is disabled until matching preview succeeds and is not expired. URL is source of grid filter/page state.

**Executable acceptance:**

```gherkin
Feature: UI-SETTLE retry-safe confirmation
  Scenario: UI-SETTLE-004 retry preserves settlement intent
    Given server preview for quotes ["q801","q802"] totals "1900.00"
    And the first settlement response is lost after the server commits settlement "S804"
    When OPERATOR "operator@srm.local" chooses Retry
    Then the second request uses the first request's Idempotency-Key
    And its JSON contains only ordered quoteIds ["q801","q802"]
    And the UI shows settlement ID "S804"
    And the statement contains no duplicate settlement entries

  Scenario: UI-LEDGER-006 reversal is a separate signed row
    Given the statement API returns settlement "975.61" and linked reversal "-975.61"
    When AUDITOR "auditor@srm.local" filters the statement to BRL
    Then two rows are visible with labels SETTLEMENT and REVERSAL
    And both rows link to the original settlement
```

**Test mapping:** `UI-SETTLE-004/UI-LEDGER-006` → Playwright BDD + network inspection. Component tests cover double click, stale preview after state change, timeout retry key, cancellation/new key, exact request schema, URL roundtrip, errors, and signed rendering.

**Verification:** Focused: `npm --prefix frontend test -- --run settlement statement`. Regression: `npm --prefix frontend run lint && npm --prefix frontend run typecheck && npm --prefix frontend test -- --run && npm --prefix frontend run build && make test-ui-features`.

**Evidence:** Captured request bodies/headers with key value redacted but equality proven by hash, Playwright trace for scenario, DOM signed rows, URL restoration, no-client-total assertion, and global handoff.

**Commit outcomes:** `feat(frontend): confirm server-previewed settlements`; `feat(frontend): add settlement and reversal ledger grid`.

---

## Prompt 09 — Observability and reviewer-ready runtime hardening

**Objective:** Make the complete financial path operable and observable without leaking or exploding cardinality.

**Prerequisites:** Prompts 02–08 green. Work on `feature/operational-hardening`.

**Scope:**

- Add bounded-cardinality metrics for simulation/quote, preview, settlement/idempotency/conflict, reversal, report, FX resilience; structured correlation/actor/event logs; separate append-only financial audit; liveness/readiness.
- Harden default reviewer Compose (backend, frontend, PostgreSQL, mock FX) with resource/timeouts, graceful startup/shutdown, non-root/read-only/minimal permissions where practical. Optional Prometheus profile may visualize mandatory metrics.
- Add host-tool-independent smoke flow: login → simulation → receivable/quote → preview → settlement → statement, plus representative metrics and correlated logs.
- Prove passwords, JWTs, idempotency keys, unrestricted financial payloads, and unbounded IDs never appear in logs or metric label values.

**Non-goals:** Production alerting platform, tracing vendor, Grafana dashboard, real secrets, or throughput claims.

**Operational contract:** Correlation ID is accepted/generated and returned. Metrics label only bounded enums/product/currency/result, never actor/record/correlation IDs. Readiness reflects dependencies while liveness reflects process.

**Executable acceptance:**

```gherkin
Feature: OBS safe financial observability
  Scenario: OBS-003 settlement conflict is observable without leaking credentials
    Given two settlement intents conflict on one receivable
    When OPERATOR request B receives 409 code "ALREADY_SETTLED" with correlation "obs-003-b"
    Then metric "srm_settlement_outcomes_total" has label result="conflict"
    And one controlled log contains correlation "obs-003-b"
    And captured logs and metrics contain neither request Idempotency-Key nor JWT
```

**Test mapping:** `OBS-003` → Cucumber plus metric registry/log capture. Log schema/redaction → integration snapshot and forbidden-pattern scan. Cardinality → static registry assertion. Full flow → Compose shell smoke.

**Verification:** Focused: `./backend/gradlew -p backend integrationTest --tests '*Observability*' && make test-log-redaction`. Regression: `docker compose up --build --wait && make smoke-financial-path && make inspect-observability && docker compose down -v && make verify`.

**Evidence:** Sanitized correlated log lines, metrics samples/label inventory, redaction scan, complete smoke response IDs/amounts, Compose health/resource output, and global handoff.

**Commit outcomes:** `feat(observability): instrument critical financial workflows`; `chore(runtime): harden reviewer Compose stack`.

---

## Prompt 10 — Deterministic E2E, performance evidence, and security gates

**Objective:** Prove the critical browser path deterministically and enforce release-blocking security/query checks.

**Prerequisites:** All implementation Prompts 01–09 green; fixed E2E fixture mechanism and hardened Compose available. Work on `feature/release-evidence`.

**Scope:**

- Run fixed clock/IDs/rates/dates browser flow: login, mandatory live simulation, receivable/quote, fresh server preview, retry-safe settlement, filtered ledger, and reversal visibility via admin API setup if no reversal UI.
- Fixtures are isolated/idempotent with exact decimals. Use events/barriers/observable states, never sleeps. Keep Playwright screenshots/traces only on failure.
- Add representative-volume generator and `EXPLAIN (ANALYZE, BUFFERS)` artifact for reporting; state dataset/hardware and do not claim production throughput.
- Run backend/frontend dependency audits, Gitleaks over history, CodeQL/SAST, Trivy filesystem/config/images, Flyway validation, SBOM, license check. Pin scanners; suppress only exact reviewed finding with owner/reason/expiry.
- Make local release and CI use the same commands. High/critical exploitable findings, secrets, skipped E2E, or migration failures block release.

**Non-goals:** Load certification, production benchmark, blanket suppression, always-retained browser secrets/traces, or claiming unavailable scanners passed.

**Evidence contract:** Scanner unavailable means blocked. Scan output is sanitized. Performance artifact includes query, plan, row count, database version, runtime environment, and limitations.

**Executable acceptance:**

```gherkin
Feature: E2E deterministic financial path
  Scenario: E2E-001 operator completes the fixed critical path
    Given stack clock is "2030-01-15T12:00:00Z" and fixture set "baseline-v1" is loaded
    When OPERATOR "operator@srm.local" completes login, live invoice simulation, receivable creation, quote, preview, and settlement
    Then live amount and quote amount are "975.61"
    And retrying the committed request displays the same settlement ID
    And one BRL SETTLEMENT row is visible after filtering by fixed assignor ID
    And no browser request calculates or supplies an authoritative total
```

**Test mapping:** `E2E-001` → Playwright BDD report with stable ID. Reversal continuation → browser/API fixture scenario. Fixture idempotency → PostgreSQL checksum. Query → plan assertion. Each scanner → version-pinned script and CI job with threshold test.

**Verification:** Focused: `make e2e-fixed && make explain-statements-representative`. Regression: `make release-check` (includes builds/tests/migrations/E2E/scans/SBOM/licenses); independently run `make security-scan` and confirm no suite reports skipped tests.

**Evidence:** E2E report and failure-only artifact policy, fixture checksum, exact amounts/IDs, EXPLAIN bundle, scanner versions/findings/suppressions, SBOM/license report, CI/local command parity, and global handoff.

**Commit outcomes:** `test(e2e): prove deterministic critical financial path`; `ci(security): enforce release security scans`.

---

## Prompt 11 — Staff artifacts and reviewer documentation

**Objective:** Reconcile documentation with implemented truth and clearly separate implemented senior depth from proposed staff evolution.

**Prerequisites:** Prompts 01–10 green; migrations, OpenAPI, scenario reports, scan/query evidence available. Work on `docs/reviewer-guide`.

**Scope:**

- Reconcile early ADR/ER with actual migrations/OpenAPI. Add C4 context/container, DDL links naming the Flyway migrations as the required DDL-scripts deliverable, permission matrix, precision examples, settlement/idempotency state and sequence diagrams, terminal reversal/ledger semantics, operations/runbook, limitations, and requirement→code/test/doc/gap traceability.
- Document proposed—not implemented—1M transactions/minute evolution with workload/capacity math, partitioning, outbox/CDC, Kafka ordering/idempotent consumers, materialized reports, strong/eventual consistency boundaries, reconciliation, backpressure/DLQ/replay, SLO, DR/RPO/RTO, and staged extraction criteria.
- Complete candid `AI_USAGE.md` using actual prompts, hallucinations/corrections, wins/costs only. README begins with full-stack start and deterministic demo, and states and justifies the chosen GitHub Flow branching strategy for this project.
- Preserve whole-settlement terminal reversal, signed ledger, server simulation/preview, and authorization limitations prominently.

**Non-goals:** Retroactive claims, fabricated AI use, implemented microservices/IaC/scale, new application behavior, remote publication, or release tag.

**Documentation contract:** Every claim is labeled Implemented, Proposed, or Known gap. Traceability includes requirement, stable scenario ID, source/test path, CI job/evidence, and status. Exact `975.61` vector is reproducible from snapshots.

**Executable acceptance (documentation checks, not artificial Gherkin):**

- Link and Mermaid checks render every diagram and resolve every local path.
- Migration-to-ER schema checker finds no missing table/key/version constraint; OpenAPI lint and endpoint inventory match docs.
- Traceability checker resolves every test/doc path and finds `SETTLE-006` in feature report, CI gate, and relevant ADR.
- Reproduction script computes/document-compares `975.61` without binary floating point.
- Claim linter rejects ambiguous phrases such as “production-ready 1M/min” and requires status labels.

**Test mapping:** `DOC-LINK-001` → link/Mermaid test; `DOC-SCHEMA-002` → migration/ER diff; `DOC-TRACE-003` → traceability schema/path resolver; `DOC-MONEY-004` → exact vector script; `DOC-CLAIM-005` → implemented/proposed claim lint.

**Verification:** Focused: `make validate-docs && make validate-traceability`. Regression: `make verify && make release-check`.

**Evidence:** Rendered diagram index, zero-broken-link/schema/OpenAPI reports, traceability coverage, exact-vector output, claim classifications, actual AI usage references, and global handoff.

**Commit outcomes:** `docs: reconcile architecture and staff evolution artifacts`; `docs: complete reviewer and AI usage guide`.

---

## Prompt 12 — Authorized collaboration, crisis, publication, and release evidence

**Objective:** Produce actual Git/review/release evidence only through explicit safe authorization gates; never substitute a narrative for an operation.

**Prerequisites:** Prompts 01–11 and `make release-check` green; staged index empty; repository owner, remote, and publication state known. Work locally until each gate is explicitly authorized.

**Scope:** Apply the following gates in order; stop at the first gate whose authorization or prerequisites are absent.

**Authorization/evidence contract:**

1. **Always-safe local gate:** inspect status/history; use focused branches; only on an unpublished branch perform an actual interactive autosquash/reorder, capture old/new hashes with `git range-diff`, and rerun full gate. Never rebase a reviewed/shared branch.
2. **Authorized remote collaboration:** only after explicit confirmation of account, remote, target, permission, and desired PR set, push without force; create real PRs using `gh`; attach test/security evidence; wait for required checks/review; merge with documented strategy. Missing authorization/credentials/checks/review = `blocked`.
3. **Isolated crisis gate:** clone the release candidate into a disposable repository whose local branch is named `main`, commit a harmless simulation-only regression there, prove its dedicated test fails, perform actual `git revert`, prove it passes, and assert the complete reverted tree equals the release-candidate tree. Never mutate the real repository's `main`, deploy the defect, use a financial/security defect, or leak secrets. This is the sole red-commit exception.
4. **Authorized publication:** before creating a public remote/changing visibility, obtain explicit owner/account/repository-name approval and pass full-history secret/privacy/license scans. Never infer consent.
5. **Authorized release:** the historical local annotated `v1.0.0` is immutable and cannot represent the remediated HEAD. Only after a reviewed merge SHA is on `main` and CI/security/E2E are green, obtain explicit approval for the exact SHA and a **new** version; create that annotated tag, push it, and publish release notes. Never move, delete, or reuse `v1.0.0`.

**Non-goals:** Force-push, unsolicited public repository, fake PR/check/release URLs, defective real-main commit, post-tag evidence commit, or tag before review.

**Acceptance/evidence cases:**

```gherkin
Feature: REL authorized release provenance
  Scenario: REL-004 authorization absence blocks publication
    Given release operator has no recorded owner approval for account and repository name
    When the publication checklist is evaluated
    Then publication status is "blocked"
    And no remote, visibility, PR, merge, or tag mutation command is executed

  Scenario: CRISIS-002 isolated revert restores the gate
    Given a disposable clone branch named "main" starts at reviewed release candidate R0
    When release operator commits the documented harmless regression as D1
    Then dedicated crisis test exits non-zero for the expected assertion
    When release operator runs git revert D1 producing R1
    Then the dedicated test and full fast gate pass
    And branch history shows D1 followed by R1
    And the complete tree at R1 equals R0
```

**Test mapping:** `REL-004` → authorization checklist dry-run test and command audit; `CRISIS-002` → dedicated harmless fixture/test plus Git tree/hash assertions. Remote gate → independently accessible URL/SHA/check API verification. Tag gate → local and remote object/SHA verification.

**Verification:** Focused: locally run `git status --short`, `git diff --cached --quiet`, `make test-local-collaboration-evidence`, and `make test-crisis-evidence`. The local collaboration script captures an actual autosquash `range-diff`. Regression: before any authorized remote action run `make release-check`. Authorized evidence only: `gh pr checks <url>`, `git show --no-patch <new-version>`, `git ls-remote --tags origin <new-version>`, and URL access checks.

**Evidence:** Old/new rebase hashes/range-diff; crisis defect/revert hashes and failing/passing output; final clean status/empty index; actual PR/repository/release URLs and check/review conclusions when authorized; exact reviewed merge SHA; annotated/local/remote tag resolution. External evidence belongs in PR/release/final report, avoiding a circular post-tag commit.

**Commit outcome:** Use prior feature commits; before review add `docs(release): prepare authorized release evidence and notes`. Merge/tag/publication are authorized remote evidence, not source commits.

## Final suite audit

Before accepting this suite’s execution, map every source requirement to a prompt, stable scenario/check ID, automated test type, command, and commit/PR gate. Confirm early Git/hooks/CI/branch policy (01), early ADR/ER (01), full-stack Compose and deterministic fixtures (02), authorization/rates (03), server live simulation/quotes (04/07), coherent preview/atomic idempotency/concurrency (05/08), terminal reversal/signed ledger (06/08), observability (09), deterministic E2E/security scans (10), staff artifacts (11), and authorization-gated real collaboration/publication/tag/crisis evidence (12). Required core behavior is never described as optional.
