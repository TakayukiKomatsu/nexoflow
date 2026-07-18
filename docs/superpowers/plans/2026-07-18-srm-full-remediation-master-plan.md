# SRM Credit Engine Full Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close every validated SRM implementation and evidence gap while preserving the approved financial and authorization contracts.

**Architecture:** Restore the executable foundation first, then run four file-owned product lanes in parallel, then add acceptance/release evidence against the stabilized contracts. A final independent verifier, security reviewer, and code reviewer gate the integrated tree.

**Tech Stack:** Java 21, Spring Boot 3.5.5, Gradle, PostgreSQL 16, Flyway, Testcontainers, Cucumber JVM 7.34.3, React 19, TypeScript 6, Vite 8, Vitest 4, Playwright Test, Docker Compose/Colima, GitHub Actions.

## Global Constraints

- Treat the existing dirty worktree as user-owned baseline; never reset, stash, blanket-stage, or discard it.
- BRL and USD are the only supported currencies.
- Financial HTTP fields are validated decimal strings; calculations use `BigDecimal`, `NUMERIC`, and `HALF_EVEN` at currency boundaries.
- PostgreSQL and Flyway remain the authoritative financial write model; new migrations are append-only.
- The browser never calculates authoritative money or settlement totals.
- Settlement and Reversal are atomic and scoped-idempotent; neither is automatically retried.
- Deny-by-default roles are OPERATOR, ANALYST, ADMIN, and AUDITOR.
- No compatibility aliases, deprecated request forms, test-only production bypasses, arbitrary sleeps, or mock-only acceptance evidence.
- Remote push, PR, merge, publication, tag, and release actions are prohibited without separate SDD 12 authorization.
- Stage exact paths only when committing; do not include unrelated worktree changes.

---

## Plan set and dependency graph

```text
Foundation and platform
  ├── Identity and Currency ───────┐
  ├── Pricing and Receivables ────┤
  ├── Settlement and Reporting ───┼── Acceptance, release, and docs
  └── Frontend Workflows ─────────┘                │
                                                    └── Integrated review/fix loops
```

- `2026-07-18-srm-foundation-platform-plan.md`
- `2026-07-18-srm-identity-currency-plan.md`
- `2026-07-18-srm-pricing-receivables-plan.md`
- `2026-07-18-srm-settlement-reporting-plan.md`
- `2026-07-18-srm-frontend-workflows-plan.md`
- `2026-07-18-srm-acceptance-release-plan.md`

## Shared interfaces frozen by the master plan

### Problem Details

Every controlled failure returns `application/problem+json` with:

```json
{
  "type": "urn:srm:error:<kebab-code>",
  "title": "<HTTP status title>",
  "status": 400,
  "detail": "<safe operator message>",
  "instance": "/api/v1/<path>",
  "code": "STABLE_MACHINE_CODE",
  "correlationId": "<request correlation id>",
  "violations": [{ "field": "<field>", "message": "<constraint>" }]
}
```

`violations` is present only for validation failures. Required new codes are `UNSUPPORTED_CURRENCY`, `FX_RATE_MISSING`, `FX_RATE_STALE`, `IDEMPOTENCY_KEY_REQUIRED`, and `PRICING_QUOTE_EXPIRED`; existing conflict codes remain stable.

### Pricing transport

`DecimalString.from(JsonNode)` accepts textual JSON nodes only, stores a `BigDecimal`, and `DecimalString.json()` serializes a plain decimal string through `@JsonValue`.

```java
record SimulationRequest(DecimalString faceAmount, String faceCurrency,
                         String productType, LocalDate dueDate,
                         String settlementCurrency) {}
record QuoteResponse(UUID id, UUID receivableId, String productType,
                     LocalDate dueDate, Response pricing, Instant expiresAt,
                     String status, String createdBy) {}
```

JSON numeric tokens for money/rate fields fail validation and all response values use canonical plain decimal strings.

### Frontend error and cancellation contract

```ts
export type ProblemDetail = {
  status?: number;
  detail?: string;
  code?: string;
  correlationId?: string;
  violations?: Array<{ field: string; message: string }>;
};

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | undefined,
    message: string,
    readonly correlationId?: string,
  ) { super(message); }
}
```

Every mutable GET/POST workflow accepts an optional `AbortSignal`. Timeouts are implemented by aborting the request and preserve an unresolved Settlement intent.

## Execution protocol

### Task 1: Restore foundation

**Files:** `backend/build.gradle`, `frontend/src/a11y.test.tsx`, `Makefile`, `frontend/package.json`, local container-runtime environment.

- [ ] Execute every task in `2026-07-18-srm-foundation-platform-plan.md`.
- [ ] Run `make verify-fast` and `make build`.
- [ ] Start Colima when Docker is absent, export the documented Testcontainers variables, and run `make test-runtime`.
- [ ] Do not start product lanes until Gradle configuration and frontend build both succeed.

### Task 2: Run product lanes in parallel

**Interfaces:** Consumes the shared Problem Detail, pricing transport, and cancellation contracts above.

- [ ] Dispatch one file-owned worker for `2026-07-18-srm-identity-currency-plan.md`.
- [ ] Dispatch one file-owned worker for `2026-07-18-srm-pricing-receivables-plan.md`.
- [ ] Dispatch one file-owned worker for `2026-07-18-srm-settlement-reporting-plan.md`.
- [ ] Dispatch one file-owned worker for `2026-07-18-srm-frontend-workflows-plan.md`.
- [ ] Require each worker to run only its focused tests and return changed paths plus command evidence.
- [ ] Resolve cross-lane API type changes centrally; workers must not edit another lane's files.

### Task 3: Integrate product lanes

- [ ] Run backend unit tests and frontend tests/typecheck/build together.
- [ ] Run PostgreSQL integration tests with a working container runtime.
- [ ] Fix compile and contract mismatches at the owning interface, not with adapters or aliases.
- [ ] Re-run the failed focused command and then the combined gates.

### Task 4: Add executable acceptance and release evidence

**Interfaces:** Consumes stabilized HTTP contracts and runtime images from Tasks 1–3.

- [ ] Execute every task in `2026-07-18-srm-acceptance-release-plan.md`.
- [ ] Run Cucumber against Spring Boot and PostgreSQL, Playwright against the real Compose stack, and the representative statement query plan against PostgreSQL 16.
- [ ] Reconcile documentation only after these runtime checks pass.

### Task 5: Independent integrated review

- [ ] Dispatch a verifier to compare every acceptance item in `../specs/2026-07-18-srm-full-remediation-design.md` with fresh command output.
- [ ] Dispatch a security reviewer over authentication, authorization, secret handling, immutable financial history, container hardening, and CI policies.
- [ ] Dispatch a code reviewer over correctness, races, precision, transaction boundaries, and maintainability.
- [ ] Convert only confirmed findings into owner-scoped fix tasks.
- [ ] Repeat focused verification, then `make release-check`, until no release-blocking finding remains.

### Task 6: Commit coherent units

- [ ] Inspect each exact path before staging because the worktree started dirty.
- [ ] Stage only the files belonging to one approved plan task.
- [ ] Commit with Conventional Commit subjects and no generated or co-authored attribution.
- [ ] Do not publish commits, tags, or releases.

## Final expected evidence

```text
make verify-fast                         PASS
make build                               PASS
make test-runtime                        PASS
make verify-compose                      PASS
make test-api-features                    PASS
make test-ui-features                     PASS
make explain-statements-representative    PASS
make security-scan                        PASS
make validate-docs                        PASS
make validate-traceability                PASS
make release-check                        PASS
```

Any missing command, skipped scenario, unavailable prerequisite, or stale document claim keeps the overall result incomplete.
