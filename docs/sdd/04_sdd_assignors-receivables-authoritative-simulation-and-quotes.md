# Prompt 04 — Assignors, receivables, authoritative simulation, and quotes

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
- Infrastructure/documentation contracts use executable shell, `docs/architecture/schema-inventory.md`, OpenAPI, `README.md` textual architecture, and local-link tests rather than artificial Gherkin; externally observable infrastructure behavior may use Gherkin.
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
