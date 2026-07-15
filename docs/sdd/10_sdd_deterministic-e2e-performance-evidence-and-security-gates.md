# Prompt 10 — Deterministic E2E, performance evidence, and security gates

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
