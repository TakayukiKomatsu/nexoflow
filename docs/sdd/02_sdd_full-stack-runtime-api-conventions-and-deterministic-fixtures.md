# Prompt 02 — Full-stack runtime, API conventions, and deterministic fixtures

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
