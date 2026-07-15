# Prompt 03 — Identity, authorization, reference rates, and resilient FX

Self-contained execution prompt for the SRM Credit Engine. Execute from the repository root. Requirements: [`docs/README_case_dev_srm.md`](../README_case_dev_srm.md), [`docs/SRM_REQUIREMENTS_PLAN.md`](../SRM_REQUIREMENTS_PLAN.md), and [`docs/CONTEXT.md`](../CONTEXT.md). The global contract below is mandatory for this increment.

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
