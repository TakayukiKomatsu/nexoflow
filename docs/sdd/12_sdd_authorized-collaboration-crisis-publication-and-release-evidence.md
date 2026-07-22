# Prompt 12 — Authorized collaboration, crisis, publication, and release evidence

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

**Objective:** Produce actual Git/review/release evidence only through explicit safe authorization gates; never substitute a narrative for an operation.

**Prerequisites:** Prompts 01–11 and `make release-check` green; staged index empty; repository owner, remote, and publication state known. Work locally until each gate is explicitly authorized.

**Scope:** Apply the following gates in order; stop at the first gate whose authorization or prerequisites are absent.

**Authorization/evidence contract:**

1. **Always-safe local gate:** inspect status/history; use focused branches; only on an unpublished branch perform an actual interactive autosquash/reorder, capture old/new hashes with `git range-diff`, and rerun full gate. Never rebase a reviewed/shared branch.
2. **Authorized remote collaboration:** only after explicit confirmation of account, remote, target, permission, and desired PR set, push without force; create real PRs using `gh`; attach test/security evidence; wait for required checks/review; merge with documented strategy. Missing authorization/credentials/checks/review = `blocked`.
3. **Isolated crisis gate:** clone the release candidate into a disposable repository whose local branch is named `main`, commit a harmless simulation-only regression there, prove its dedicated test fails, perform actual `git revert`, prove it passes, and assert the complete reverted tree equals the release-candidate tree. Never mutate the real repository's `main`, deploy the defect, use a financial/security defect, or leak secrets. This is the sole red-commit exception.
4. **Authorized publication:** before creating a public remote/changing visibility, obtain explicit owner/account/repository-name approval and pass full-history secret/privacy/license scans. Never infer consent.
5. **Authorized release:** only after reviewed merge SHA is on `main` and CI/security/E2E green, obtain explicit approval for exact SHA and version; create immutable annotated `v1.0.0`, push tag, publish release notes. Never move/reuse tag.

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
    And the tree at R1 equals R0 for the simulation target
```

**Test mapping:** `REL-004` → authorization checklist dry-run test and command audit; `CRISIS-002` → dedicated harmless fixture/test plus Git tree/hash assertions. Remote gate → independently accessible URL/SHA/check API verification. Tag gate → local and remote object/SHA verification.

**Verification:** Focused: locally run `git status --short && git diff --cached --quiet && make test-crisis-evidence && git range-diff <old>...<new>`. Regression: before any authorized remote action run `make release-check`. Authorized evidence only: `gh pr checks <url>`, `git show --no-patch v1.0.0`, `git ls-remote --tags origin v1.0.0`, and URL access checks.

**Evidence:** Old/new rebase hashes/range-diff; crisis defect/revert hashes and failing/passing output; final clean status/empty index; actual PR/repository/release URLs and check/review conclusions when authorized; exact reviewed merge SHA; annotated/local/remote tag resolution. External evidence belongs in PR/release/final report, avoiding a circular post-tag commit.

**Commit outcome:** Use prior feature commits; before review add `docs(release): prepare v1.0.0 evidence and notes`. Merge/tag/publication are authorized remote evidence, not source commits.

## Final suite audit

Before accepting this suite’s execution, map every source requirement to a prompt, stable scenario/check ID, automated test type, command, and commit/PR gate. Confirm early Git/hooks/CI/branch policy (01), early ADR/ER (01), full-stack Compose and deterministic fixtures (02), authorization/rates (03), server live simulation/quotes (04/07), coherent preview/atomic idempotency/concurrency (05/08), terminal reversal/signed ledger (06/08), observability (09), deterministic E2E/security scans (10), staff artifacts (11), and authorization-gated real collaboration/publication/tag/crisis evidence (12). Required core behavior is never described as optional.
