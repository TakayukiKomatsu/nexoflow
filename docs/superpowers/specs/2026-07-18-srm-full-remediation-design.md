# SRM Credit Engine Full Remediation Design

**Status:** Approved design
**Date:** 2026-07-18
**Scope:** Strict closure of every implementation, integrity, frontend, evidence, CI, security, operational, and documentation gap identified by the backend/frontend validation.

## 1. Objective

Bring the SRM Credit Engine from a substantial but non-release-ready implementation to a locally reproducible delivery that satisfies the original case brief and the canonical SDD 01–11 contracts.

Completion requires working code and executable evidence. File presence, mocked controller tests, static source scans, or an unavailable prerequisite cannot be reported as passing evidence.

Remote pushes, pull requests, merges, repository publication, visibility changes, tags, and releases remain outside this remediation. They require the separate explicit authorization gates in SDD 12.

## 2. Governing contracts

The remediation preserves these existing decisions:

- Java 21 and Spring Boot modular monolith.
- React and TypeScript operator SPA.
- PostgreSQL and Flyway as the authoritative financial write model.
- SQL reporting adapter for the settlement ledger.
- BRL and USD as the only supported currencies.
- `BigDecimal`, PostgreSQL `NUMERIC`, decimal-string API fields, and `HALF_EVEN` currency-boundary rounding.
- Server-authoritative pricing simulations and settlement previews.
- Immutable 15-minute Pricing Quotes.
- Atomic, scoped-idempotent Settlement with optimistic locking and database uniqueness safeguards.
- Whole, terminal Settlement Reversal represented as a negative compensating ledger movement.
- Deny-by-default authorization with OPERATOR, ANALYST, ADMIN, and AUDITOR roles.
- No automatic retry around Settlement or Settlement Reversal.

Clean cutover is required. No compatibility aliases, duplicate endpoints, deprecated request forms, or test-only production bypasses remain after remediation.

## 3. Problem inventory

### 3.1 Executable foundation

- Gradle references `integrationTestImplementation` before creating the integration-test configuration.
- Frontend typecheck and build fail on an unused import.
- CI invokes an undefined `license-check` target.
- Multiple verification targets named by the SDD do not exist.
- Container-dependent gates cannot currently run because no Docker-compatible runtime is installed.

### 3.2 Identity, Currency, and observability

- FX observations can be updated or deleted.
- Equal unsupported currency codes can receive identity conversion.
- Missing or stale FX becomes an uncontrolled internal error.
- Provider retries include non-transient failures and use linear delay without jitter.
- FX latency and complete resilience evidence are missing.
- Authorization tests do not cover the complete endpoint-role matrix.
- Prometheus is exposed by Actuator configuration but lacks an explicit security matcher usable by the inspection path.
- Runtime log redaction and authenticated actor logging are not proven.

### 3.3 Receivables and Pricing

- Product Strategy implementations are behaviorally identical while orchestration owns product-spread selection.
- Financial API fields do not uniformly reject JSON numeric tokens in favor of decimal strings.
- Unsupported currencies can reach reference/FX lookup and produce internal errors.
- Quote GET serialization can change `975.61` to `975.6100` after persistence.
- Pricing Quote snapshots and responses do not expose every authoritative reproduction input.
- Receivable responses expose money as JSON numbers.
- Required cheque, fractional-term, direct/inverse FX, `HALF_EVEN`, persistence, immutability, and expiry-boundary vectors are absent.

### 3.4 Settlement, Reversal, Reporting, and Audit

- Settlement Reversal rows can be updated or deleted.
- Missing `Idempotency-Key` is not mapped to the controlled validation contract.
- Settlement request DTOs silently ignore forbidden calculated fields.
- Same-key concurrency is not deterministically synchronized after the claim point.
- One integration test class is not isolated between methods.
- Reversal idempotency and terminal-state acceptance are mock-only.
- Settlement and reversal ledger movements reuse the same entry ID.
- Reporting boundary, combined-filter, role, pagination, injection, query-count, and query-plan evidence is absent.
- Reference-data mutations are not comprehensively represented in the append-only audit trail.

### 3.5 Frontend workflows

- Form changes do not invalidate a previously registered Receivable ID.
- A failed simulation refresh can make an old amount appear current.
- Read-only roles trigger simulation requests they cannot authorize.
- Quote breakdown presentation is incomplete.
- Receivable and quote creation allow duplicate submission.
- Field-level accessibility semantics point at the wrong invalid control.
- Settlement Preview responses can overwrite a newer quote selection.
- Expired/stale Settlement errors are not classified reliably.
- Problem Detail codes are discarded, conflating settlement recovery paths.
- Settlement confirmation has no bounded timeout.
- Confirmation remains visually enabled after Preview expiry until clicked.
- Statement responses can overwrite newer URL filter state.
- OPERATOR and ANALYST cannot reach the ledger UI despite backend authorization.
- Required browser and network-level evidence is absent.

### 3.6 Acceptance and release evidence

- Cucumber dependencies exist without feature files, glue, or a suite runner.
- Playwright and `E2E-001` are absent.
- Representative reporting data and `EXPLAIN (ANALYZE, BUFFERS)` evidence are absent.
- Runtime log-redaction evidence is absent.
- Local and CI release/security command parity is incomplete.
- Trivy does not cover built runtime images and uses an overly broad unfixed-finding policy.
- README, runbook, architecture documentation, and traceability contain stale or contradictory status claims.

## 4. Team topology and ownership

Work runs in dependency-ordered waves. Each execution lane owns disjoint production files and its adjacent tests. Shared build contracts, public API records, migrations, Make targets, and documentation are changed by one designated owner at a time.

### Foundation owner

Owns:

- `backend/build.gradle`
- `frontend/src/a11y.test.tsx`
- root `Makefile`
- prerequisite dependency and container-runtime setup

No feature lane starts until the baseline build and fast verification commands can execute.

### Identity and Currency owner

Owns identity/currency production packages, FX migrations, security authorization tests, resilience tests, and metrics for the external FX boundary.

### Receivables and Pricing owner

Owns assignor/receivable/pricing production packages, quote schema evolution, exact API DTOs, Strategy interfaces, and pricing contract tests.

### Settlement and Reporting owner

Owns settlement/reporting/audit production packages, reversal and ledger migrations, settlement API strictness, concurrency/rollback tests, and reporting query evidence.

### Frontend owner

Owns `App.tsx`, `SettlementWorkspace.tsx`, the API client, styles required for behavior/accessibility, component tests, and Playwright workflow implementation.

### Platform and release-evidence owner

Owns runtime health/logging configuration, Compose and scripts, CI security policy, Make release targets, Cucumber suite infrastructure, representative-data/query-plan scripts, and local/CI parity.

### Documentation owner

Runs only after behavior and evidence stabilize. Reconciles README architecture, runbook, permission matrix, schema inventory, AI usage, and requirement traceability with observed truth.

## 5. Execution waves

### Wave 0: executable foundation

1. Create the integration-test source set and configurations before declaring their dependencies.
2. Remove the frontend typecheck blocker.
3. Add explicit Make targets for all documented verification commands.
4. Install or activate Colima plus Docker CLI when no compatible runtime exists.
5. Run fast tests and builds. Any unexpected failure is corrected before Wave 1.

### Wave 1: product corrections in parallel

#### Identity and Currency

- Add PostgreSQL update/delete guards for immutable FX observations.
- Validate active BRL/USD codes before identity/direct/inverse conversion.
- Introduce stable domain errors and RFC 9457 codes for unsupported, missing, and stale FX.
- Retry only transient transport/5xx failures.
- Use bounded exponential backoff with injectable jitter.
- Record bounded FX attempt latency and result metrics.
- Add complete role matrix, exact 24-hour boundary, persistence, retry, circuit, metrics, and redaction scenarios.

#### Receivables and Pricing

- Replace permissive money transport with decimal-string request and response DTOs.
- Use one supported-currency value object at every pricing and receivable boundary.
- Deepen the Strategy seam so the selected Strategy owns product-specific risk/spread behavior while the engine owns common term, discount, conversion, and rounding flow.
- Persist and return all authoritative quote inputs and snapshots.
- Normalize quote money outputs consistently to currency scale.
- Add independent exact vectors and PostgreSQL snapshot/immutability evidence.

#### Settlement, Reversal, Reporting, and Audit

- Add immutable reversal guards.
- Map missing/blank/oversized idempotency headers to stable validation Problem Details.
- Reject unknown settlement/reversal request fields.
- Preserve exact replay status/body semantics.
- Add deterministic post-claim concurrency barriers and isolated database assertions.
- Give every ledger movement a unique stable entry ID while preserving links to original Settlement and optional Reversal.
- Complete audit coverage for financial/reference mutations using safe metadata.
- Add real PostgreSQL statement and reversal acceptance scenarios.

#### Frontend

- Model registered Receivable, current simulation input, quote, preview, and settlement intent as explicit dependent states.
- Invalidate dependent state whenever upstream inputs change.
- Sequence or cancel all simulation, preview, and statement requests.
- Preserve stale/error state until a result matching current inputs succeeds.
- Parse RFC 9457 `code` and map each recovery path explicitly.
- Retain the idempotency key only for the same unresolved intent; cancellation or a proven key conflict starts a new intent.
- Enforce bounded request timeout, expiry-aware confirmation, and duplicate-submit prevention.
- Render complete quote breakdowns and every authorized ledger role.
- Add per-field validation semantics, focus recovery, keyboard flow, and announcement coverage.

#### Platform and operations

- Define a protected Prometheus access policy and make the inspection script use it.
- Capture bounded actor/role context before security teardown.
- Make seeding idempotently reconcile required roles for existing local users.
- Use one canonical FX-provider configuration property for provider and health indicator.
- Complete image, filesystem, secret, dependency, license, SBOM, and SAST gates with narrow reviewed suppressions only.

### Wave 2: executable acceptance

- Implement Cucumber-JUnit Platform features and glue for stable SDD scenario IDs against Spring and PostgreSQL Testcontainers.
- Implement Playwright browser scenarios for login, live simulation, Receivable, Quote, fresh Preview, retry-safe Settlement, ledger filters, and Reversal visibility.
- Add deterministic fixtures and event/state synchronization without arbitrary sleeps.
- Generate representative statement data and capture `EXPLAIN (ANALYZE, BUFFERS)` with dataset, PostgreSQL version, environment, plan, and limitations.
- Capture sanitized runtime logs and metric label inventories.
- Implement release, security, documentation, traceability, UI/API feature, query-plan, and crisis-evidence Make targets.

### Wave 3: documentation and integrated verification

- Reconcile all implemented/proposed/gap claims.
- Run independent verifier, security reviewer, and code reviewer lanes.
- Convert every failed gate into a bounded fix task.
- Repeat execution and verification until all required local gates are green.

## 6. Data and migration design

New Flyway migrations are append-only; existing applied migrations are not rewritten.

Required schema evolution includes:

- Immutable `exchange_rates` update/delete trigger.
- Immutable `settlement_reversals` update/delete trigger.
- Complete Pricing Quote reproduction columns where missing.
- Stable unique ledger movement identity, either persisted explicitly or deterministically derived without collisions between settlement and reversal movements.
- Supporting indexes demonstrated by representative query plans.

Migration tests run against real PostgreSQL and prove both allowed state transitions and forbidden mutations.

## 7. API and error design

All financial amounts and rates cross the HTTP boundary as validated decimal strings. Unknown request fields are rejected.

Expected domain failures receive stable RFC 9457 codes, including:

- Unsupported currency.
- Missing FX rate.
- Stale FX rate.
- Missing or invalid idempotency key.
- Reused idempotency key with different payload.
- Already settled.
- Already reversed.
- Expired or invalid Pricing Quote.

Authentication failures remain 401, authorization failures 403, validation failures 400, state conflicts 409, and unavailable external dependencies 503. Unexpected failures remain controlled 500 responses without implementation details.

## 8. Frontend state design

State dependencies are explicit:

```text
Pricing inputs
  -> server simulation
  -> registered Receivable
  -> immutable Pricing Quote selection
  -> matching fresh Settlement Preview
  -> settlement intent with one idempotency key
  -> Settlement result
  -> URL-backed ledger projection
```

Changing an upstream state invalidates every dependent state. Network responses carry a request identity and are ignored if they no longer match current state. The browser never computes authoritative money or totals.

## 9. Verification gates

Completion requires fresh successful evidence for:

- Backend unit and architecture tests.
- Frontend tests, accessibility, lint, typecheck, and build.
- PostgreSQL migrations, concurrency, rollback, immutability, authorization, pricing, settlement, reversal, reporting, and Cucumber scenarios.
- Playwright deterministic critical path.
- Compose startup, financial smoke, fixture idempotency, readiness loss/recovery, and metrics inspection.
- Runtime log redaction and bounded metric cardinality.
- Representative reporting query plan.
- CodeQL, Trivy filesystem/config/image, secret scan, dependency audit, SBOM, and license policy.
- Documentation links, README architecture, schema, OpenAPI, traceability, exact-money reproduction, and claim classification.
- Full `release-check` local/CI command parity.

A blocked prerequisite is reported as blocked and resolved where locally controllable; it is never relabeled as passed.

## 10. Non-goals

- Real microservices.
- Real OIDC or external market-data provider.
- Refresh tokens.
- Production infrastructure or claims of one million transactions per minute.
- Remote publication, PR creation/merge, tag creation/push, or release publication.
- Unrelated refactoring or visual redesign.

## 11. Acceptance

The remediation is complete only when:

1. Every validated defect in this document is fixed or superseded by a documented, executable contract that preserves the original requirement.
2. Every required verification command exists and succeeds locally.
3. Backend, frontend, Testcontainers, Compose, Cucumber, Playwright, performance, security, and documentation gates are green.
4. Financial history is immutable except for the explicitly allowed Quote consumption and Receivable lifecycle transitions.
5. Frontend state cannot display or confirm a financial result for stale or mismatched inputs.
6. Independent verifier, security reviewer, and code reviewer report no release-blocking findings.
7. Documentation describes implemented truth without fabricated or stale evidence.
