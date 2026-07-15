# Prompt 06 — Whole-settlement reversal, append-only audit, and ledger

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

**Objective:** Define terminal correction semantics and an explicit compensating-entry statement ledger.

**Prerequisites:** Prompt 05 integrity gate green. Work on `feature/reversal-ledger`; do not weaken settlement invariants.

**Scope:**

- ADMIN-only whole-settlement reversal; required reason and idempotency key; one immutable reversal. Original settlement/items/quotes remain unchanged; affected receivables become terminal `REVERSED` and can never settle again. Correction requires a new receivable/quote.
- Same reversal key/hash replays original; a different key after reversal → `409 ALREADY_REVERSED`. Reversal, receivable transitions, idempotency outcome, and audit event commit atomically.
- Append-only audit events capture actor/action/target/time/correlation/safe metadata for reference mutation, quote, settlement, reversal. Audit detail is ADMIN/AUDITOR.
- `GET /api/v1/settlement-statements` is a ledger: one positive SETTLEMENT entry/item and one negative REVERSAL entry/reversed item; signed amount, `effectiveAt`, original settlement ID, optional reversal ID, immutable item dimensions. Default includes both types.
- `[from,to)` UTC filters apply to entry `effectiveAt`; deterministic `effectiveAt DESC, entryId DESC`; bounded server pagination; parameterized optimized SQL; no N+1. OPERATOR/ANALYST/ADMIN/AUDITOR have role-wide statement read because no tenant ownership model exists.

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
