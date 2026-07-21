# Validated plan remediation design

**Status:** approved design
**Date:** 2026-07-19
**Scope:** Close every high-confidence discrepancy found by independent implementation-vs-plan validation, then verify the complete senior/staff acceptance boundary in `docs/README_case_dev_srm.md` and canonical SDD 01–11 contracts. SDD 12 remote publication, tags, and release remain authorization-gated and are not performed.

## Objective

Deliver a locally reproducible SRM Credit Engine whose observed behavior, executable evidence, and documentation agree. The browser must consume the actual backend wire contract; financial authority stays on the server. Coverage percentages and static paths are insufficient when they do not exercise the required contract.

## Non-negotiable contracts

- Java 21/Spring Boot modular monolith; React/TypeScript SPA; PostgreSQL/Flyway authority.
- BRL and USD only; decimal strings across HTTP; `BigDecimal`/`NUMERIC` and `HALF_EVEN` financial rounding.
- Strategy-based pricing; immutable 15-minute quotes; server-authoritative simulation and preview.
- Atomic, scoped-idempotent settlement with database uniqueness and concurrency control.
- Whole terminal reversal, immutable audit history, signed ledger, and parameterized reporting.
- Deny-by-default authorization, structured logs/metrics, deterministic runtime fixtures, and Docker Compose.
- Staff artifacts describe high-scale/EDA/IaC evolution as **Proposed**, never as delivered throughput proof.

## Remediation

### 1. Quote response contract

Model `PricingQuote` exactly as `PricingController.QuoteResponse`: `productType` and `dueDate` are top-level quote fields; `pricing` contains only the calculation breakdown. Render metadata from those top-level fields. Update all fixtures to use the real JSON response shape.

A regression test must use the real shape and assert the quote-history card shows product type and due date. The Playwright critical path must assert the same browser-visible fields.

### 2. Frontend authority and error proof

Add deterministic frontend tests covering:

- Cross-currency response rendering, including the FX source/rate returned by the server.
- Stale-FX failure surfaced as an actionable controlled UI error.
- The authoritative state/rendering path contains no financial formula or local calculation.

The test data may describe expected server responses but must not calculate financial results in the browser.

### 3. OpenAPI-to-client guard

Add a reproducible validation command that starts from the generated OpenAPI contract and asserts the quote response fields consumed by the TypeScript client. It must fail if quote metadata is nested in `pricing` or required calculation fields are absent. It uses existing project tooling; no generated-client dependency or parallel API surface is introduced.

### 4. Browser failure evidence

Configure Playwright to retain trace, screenshot, and video for every failed run, including a first-attempt local failure. Browser scenarios remain deterministic and use request/response or DOM-state synchronization rather than sleeps.

### 5. Traceability and claim correction

- Map every SDD 04–11 required supplementary check, including browser, no-formula, cross-currency, stale-FX, observability, security, and documentation checks, to executable source and command evidence.
- Add `DOC-LINK-001`, `DOC-SCHEMA-002`, `DOC-TRACE-003`, `DOC-MONEY-004`, and `DOC-CLAIM-005` to the stable matrix.
- Replace the unsupported guarantee that representative reporting plans use indexes with the accurate contract: parameterized/indexable filters, stable server pagination, no per-row query behavior, and an observed representative PostgreSQL plan whose scan selection is data/planner dependent.
- Preserve only factual `Implemented`, `Proposed`, or `Gap` labels and connect UI scenario rows to their browser evidence.

### 6. Complete senior/staff verification

Verify every original-brief requirement through the existing canonical implementation and proof paths: currency updates/mock FX; Strategy pricing and cross-currency conversion; relational ACID settlement; REST/OpenAPI; optimized filtered statement; layered backend; live operator simulation; server-paginated transaction grid; controlled errors; acceptance criteria for usability/security/performance/scale; Docker, hooks, CI, observability, resilience, concurrency; C4/ADR/ER/DDL; GitHub Flow/crisis evidence; proposed 1M TPM, partitioning, and EDA design; AI-use disclosure.

Only local, authorized commands are run. Remote publication, PR mutation, tags, and releases are left blocked as required by SDD 12.

## Verification

Completion requires all of the following to pass freshly:

1. Focused frontend contract, FX-state, and no-formula tests.
2. The OpenAPI-to-client validation command.
3. Playwright critical path, including quote metadata assertions and failure-artifact configuration.
4. `make test-coverage`, frontend typecheck/build, and relevant backend acceptance/integration tests.
5. `make validate-docs` and `make validate-traceability`.
6. Full applicable local senior/staff verification targets, with unavailable external prerequisites reported as blocked rather than passed.

## Acceptance

The work is complete when the quote card uses the real API response, all previously missing proof paths execute, documentation claims match observed evidence, and every locally executable command required by the original brief and SDD 01–11 succeeds. No compatibility alias, test-only production bypass, fabricated evidence, or production-scale claim is introduced.
