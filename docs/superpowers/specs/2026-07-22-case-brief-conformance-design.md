# Case brief conformance design

**Status:** approved remediation direction; awaiting specification review
**Date:** 2026-07-22
**Source of acceptance:** [`docs/README_case_dev_srm.md`](../../README_case_dev_srm.md)
**Scope:** Make every locally controllable mandatory senior/staff requirement in the case brief true in code, local Git evidence, documentation placement, and executable verification. Terraform/Kubernetes remains optional by owner decision. Hosted repository, hosted pull request/review, remote CI evidence, and release publication require explicit owner authorization and are not performed.

## Objective

Deliver a SRM Credit Engine that satisfies the case brief literally where local implementation can establish proof. Existing green local release gates are necessary but insufficient: the solution must have distinct financial-module layers, controlled frontend render-failure recovery, required README content, and real local evidence for the requested Git practices.

## Current verified boundary

`make release-check` passed on `main` on 2026-07-22. The existing implementation already proves currency management and mocked FX; exact Strategy pricing and final-step cross-currency conversion; PostgreSQL ACID/idempotent settlement with concurrency/rollback coverage; REST/OpenAPI; native-SQL filtered statements; server-authoritative operator simulation; server-paginated ledger; Compose; security and documentation gates; C4, ADR, ER, DDL, AI disclosure, observability, resilience, and optimistic locking.

The following gaps remain against the source brief:

1. Five financial modules combine business rules and JDBC persistence in `Jdbc*Service` classes rather than separating application, business/domain, and persistence layers.
2. `App.tsx` and `SettlementWorkspace.tsx` combine presentation and stateful orchestration; no boundary catches unexpected React render failures.
3. The root README omits the required inline GitHub Flow rationale and the 1M-transactions-per-minute architecture design.
4. No local PR simulation artifact or executable interactive-rebase/autosquash proof exists.
5. The crisis proof runs on a disposable branch rather than the disposable clone's `main` branch.
6. Local annotated `v1.0.0` points to an older commit, while documentation incorrectly says tags have never been executed.

## Non-negotiable contracts

- Java 21/Spring Boot modular monolith, React/TypeScript SPA, PostgreSQL/Flyway authority.
- BRL/USD only; decimal strings over HTTP; `BigDecimal`/`NUMERIC` and `HALF_EVEN` at money boundaries.
- Strategy pricing, immutable 15-minute quotes, server-authoritative simulation and preview, and no browser financial arithmetic.
- Atomic scoped-idempotent settlement, optimistic locking, whole-settlement reversal, immutable audit history, and parameterized reporting.
- Reporting retains its allowed two-layer read-model structure.
- No test-only production bypass, compatibility alias, moved/reused Git tag, fabricated hosted evidence, deliberate defect on the real repository `main`, or unsupported production-scale claim.

## Design

### Financial module layering

For each of `assignor`, `receivable`, `currency`, `pricing`, and `settlement`, establish an explicit three-layer dependency direction:

```text
api -> application -> domain
                  -> infrastructure
infrastructure -> domain/application ports
```

- **Domain:** financial invariants, state transitions, value types, and repository ports. It must not import Spring, JDBC, or HTTP.
- **Application:** focused use cases that coordinate domain policies through ports; no raw SQL.
- **Infrastructure:** JDBC implementations of ports and Spring wiring. SQL, row mapping, locks, and persistence exceptions remain here.
- **API:** request validation and response mapping only.

The refactor preserves endpoint payloads, SQL behavior, migrations, locking order, idempotency, and current public API contracts. Architecture tests enforce the dependency rule for every financial module, not merely the identity package.

### Frontend boundaries and failure recovery

- Extract workflow state/effects into named hooks and render-only regions into named components. The API client remains the only transport boundary; financial calculation remains server-only.
- Add a top-level React error boundary with a recoverable, accessible fallback. It records no sensitive values, offers a retry/reload action, and does not obscure expected API error handling.
- Add tests that prove a child render exception presents the fallback; retain existing tests for controlled API/transport errors, simulation debounce/cancellation, settlement retry, and server pagination.

### Required README and staff artifacts

- Put a concise GitHub Flow rationale directly in `README.md`, retaining the detailed policy document as the authoritative linked procedure.
- Put a concise 1M-transactions-per-minute evolution section directly in `README.md`: ingress idempotency, outbox/event stream, partitioning by assignor/time, read-model materialization, cache/versioning, sharding evolution, strong financial writes within a partition, eventual analytic projections, backpressure/DLQ/replay, SLO/capacity/DR prerequisites. Label it **Proposed**, not implemented throughput proof.
- Add a committed local PR-simulation artifact that records scope, tests, security, migrations, rollback, residual risks, source/target branch, and review conclusion. It must be visibly a local simulation, not a fictitious hosted URL.

### Safe Git-practice proof

- Add an executable test that creates a disposable clone, makes a harmless `fixup!` commit, runs noninteractive autosquash interactive rebase with a deterministic sequence editor, and proves the resulting history is linear and has no `fixup!` subject.
- Change crisis proof to create a disposable clone whose checked-out branch is named `main`; inject a harmless dedicated-fixture regression there, prove its targeted test fails, revert it, then prove the fixture and fast gate recover. The working repository and its `main` remain untouched.
- Correct documentation to distinguish the existing stale local `v1.0.0` tag from unperformed remote publication/release. Do not move, delete, or reuse `v1.0.0`.

### Tag and remote-release boundary

A final local tag must be a new semantic version at an approved final SHA. Creating it, configuring a remote, publishing a repository, opening/reviewing a hosted PR, observing hosted CI, pushing a tag, and publishing a release are excluded until the owner provides the account, repository name, visibility, exact SHA, version, and explicit authorization. Until then, docs state this boundary accurately.

## Verification

Completion requires fresh passing evidence for:

1. New domain-layer architecture tests and all affected backend unit/integration/Cucumber tests.
2. New frontend error-boundary and separation tests plus typecheck/build and Playwright critical path.
3. Disposable local PR/rebase and disposable-`main` crisis/revert tests.
4. `make validate-docs` and `make validate-traceability` with documentation wording/paths resolved.
5. `make release-check` after all local changes, with generated timing-only EXPLAIN differences restored rather than committed.
6. An independent review of the final diff.

## Acceptance

The remediation is complete when every locally controllable mandatory clause in `docs/README_case_dev_srm.md` has concrete implementation and executable evidence, including the literal three-layer financial backend, frontend recovery/separation, in-README staff design content, and safe Git-process demonstrations. Remote/publication requirements remain explicitly authorization-gated; optional Terraform/Kubernetes remains absent by owner decision.
