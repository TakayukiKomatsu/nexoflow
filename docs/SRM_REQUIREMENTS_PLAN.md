# SRM Credit Engine — Requirements and Delivery Plan

## 1. Delivery target

The delivered local implementation demonstrates **senior-level engineering depth** and includes selected **staff-level architecture and governance artifacts**. `make release-check` is the aggregate evidence path.

The objective was not to build a distributed production platform in 3–4 days. The repository delivers a modular monolith, executable evidence for critical financial paths, and a proposed evolution to very high scale. It does not claim production capacity.

### Implemented at senior depth

- Correct decimal financial calculations
- Layered modular backend
- Strategy-based pricing
- Atomic and idempotent settlement
- Concurrency control
- Authentication and authorization
- Optimized reporting query
- Automated tests and CI
- Structured logs, metrics, and health checks
- Docker-based local environment
- React operator interface

### Staff-level artifacts

- Architecture Decision Records (ADRs)
- C4 context and container diagrams
- Explicit Git workflow and crisis/hotfix simulation
- Evolution design for 1 million transactions per minute
- Event-driven architecture proposal
- Data partitioning, caching, and consistency analysis
- Executable local acceptance, security, documentation, and crisis/revert evidence

### Explicitly not implemented

- Real microservices
- Real external identity provider
- Real market-data provider
- Kubernetes/Terraform unless time remains
- Actual infrastructure capable of 1 million transactions per minute

These are documented as evolution paths rather than simulated with unnecessary complexity.

---

## 2. Technology decisions

### Backend

- Java 21
- Spring Boot 3
- Spring Web MVC
- Spring Security
- Spring JDBC for transactional command and reporting paths; JPA for runtime integration/schema validation
- PostgreSQL 16
- Flyway SQL and Java migrations (V1–V23)
- Bean Validation
- springdoc-openapi
- Bounded retry and circuit-breaker behavior implemented in the FX HTTP adapter; no Resilience4j dependency
- Micrometer with Prometheus registry
- JUnit 5, AssertJ, Mockito, Testcontainers, and Cucumber

### Frontend

- React with TypeScript and Vite
- Contract-aligned fetch client and explicit component state; no React Router, TanStack Query/Table, React Hook Form, or Zod dependency
- Vitest, React Testing Library, and jest-axe
- Playwright for the critical financial-path E2E scenario

### Delivery tooling

- Docker and Docker Compose
- GitHub Actions
- Conventional Commits
- Pre-commit hooks
- OpenAPI-generated or contract-aligned frontend client
- Mermaid diagrams stored as source

---

## 3. Chosen domain rules

### 3.1 Money

- Monetary values use Java `BigDecimal`; JavaScript never performs authoritative calculations.
- Database monetary values use `NUMERIC(19,4)`.
- Internal rates use `NUMERIC(19,10)`.
- BRL and USD settlement outputs are rounded to two decimal places with `HALF_EVEN`.
- Intermediate calculations retain at least ten decimal places and are rounded only at defined boundaries.
- Amounts must be strictly positive.

### 3.2 Rates

- All rates are stored as decimal fractions: `1.5% = 0.015`.
- The base rate is a versioned monthly rate selected by asset currency and effective date.
- Product spreads are versioned configuration records rather than mutable constants.
- Initial spreads:
  - Mercantile invoice: `0.015` per month
  - Post-dated cheque: `0.025` per month
- A Pricing Quote stores immutable snapshots of the base rate, spread, strategy, term convention, face values, and FX observation used.

### 3.3 Term convention

- The pricing date and due date determine whole calendar days.
- Due date must be later than the pricing date.
- Monthly term is `days / 30`, calculated as a decimal.
- Compound discounting supports fractional months using a high-precision decimal power implementation.
- The convention is named `ACTUAL_DAYS_30_MONTH` and stored with the calculation snapshot.

### 3.4 Pricing

```text
monthlyDiscountRate = baseRate + productSpread
termInMonths        = daysBetween(pricingDate, dueDate) / 30
presentValue        = faceValue / (1 + monthlyDiscountRate) ^ termInMonths
```

- Same-currency settlement uses the rounded present value.
- Cross-currency conversion happens after discounting.
- Pricing strategies provide product-specific spread/risk behavior; the orchestrator owns common calculation flow.

### 3.5 Foreign exchange

An FX pair follows `BASE/QUOTE` semantics:

```text
1 BASE = rate QUOTE
```

Example: `USD/BRL = 5.20` means `USD 1 = BRL 5.20`.

- Direct conversion multiplies by the rate.
- Inverse conversion divides by the rate.
- Rates are immutable historical observations with `observedAt` and source.
- Pricing selects the latest rate observed at or before the quote creation time.
- Rates older than 24 hours are rejected as stale.
- No triangulation is implemented initially.

### 3.6 Receivables and quotes

- An assignor owns receivables.
- A receivable has one product type, face currency, face value, issue date, due date, and status.
- A pricing simulation is server-authoritative and non-persisted; a separate pricing-quote request creates the persisted, auditable quote.
- Quotes expire after 15 minutes.
- A quote records all inputs and calculation snapshots.
- Settlement must use a valid, unexpired quote and revalidate receivable state.

### 3.7 Settlement

- One settlement request may contain multiple quoted receivables belonging to the same assignor and settlement currency.
- The batch is all-or-nothing.
- One invalid, expired, or already-settled item rejects the entire batch.
- `Idempotency-Key` is required for settlement creation.
- Repeating the same key and payload returns the original response.
- Reusing a key with a different payload returns `409 Conflict`.
- Receivables use optimistic locking through a version column.
- A database uniqueness constraint guarantees that each receivable can be settled only once.
- Completed settlements are immutable; corrections use reversal records rather than updates.

---

## 4. Authentication and authorization

The exercise implements local authentication using Spring Security.

### Authentication

- Users are stored in PostgreSQL.
- Passwords are hashed with BCrypt.
- Login returns a signed JWT access token with a 15-minute lifetime.
- Refresh tokens are deliberately omitted; users authenticate again.
- Independently configured development `OPERATOR` and `ADMIN` users are available only through the `dev` profile; test and production profiles seed none.

### Roles

The implemented role and endpoint contract is maintained in [`PERMISSION_MATRIX.md`](PERMISSION_MATRIX.md). `OPERATOR` runs the financial workflow, `ANALYST` has read-only financial access, `ADMIN` manages reference data, reads Prometheus metrics, and exclusively performs reversals, and `AUDITOR` adds audit-event visibility to read-only financial access. There is no user-management endpoint.

### Security controls

- Endpoint authorization is deny-by-default.
- Financial records are never authorized solely from client-provided ownership data.
- Login attempts are rate-limited at the application boundary.
- JWT secret/key comes from environment configuration.
- Errors do not expose stack traces or database details.
- Audit records capture actor, action, target, timestamp, correlation ID, and relevant immutable metadata.

A production evolution would replace local authentication with an OIDC provider such as Keycloak, Auth0, or the organization’s identity platform.

---

## 5. Backend modules and layers

The backend is a modular monolith organized by business capability:

```text
com.srm.creditengine
├── identity
├── assignor
├── receivable
├── currency
├── pricing
├── settlement
├── reporting
├── audit
└── shared
```

Each transactional module may contain:

```text
api             REST controllers and API DTOs
application     use cases, commands, queries, transaction boundaries
domain          aggregates, value objects, policies, ports
infrastructure  persistence and external adapters
```

Rules:

- Controllers do not contain business logic.
- Domain code does not depend on Spring or persistence annotations where practical.
- Application services control transactions.
- Spring JDBC owns transactional command paths; JPA supplies runtime integration and schema validation.
- Reporting bypasses aggregate reconstruction and uses read-optimized Spring JDBC SQL.
- Cross-module dependencies are explicit and tested with architecture tests.

---

## 6. Primary API surface

All endpoints are versioned under `/api/v1`.

### Identity

- `POST /auth/login`
- `GET /users/me`

### Reference and currency data

- `POST /exchange-rates`
- `GET /exchange-rates`
- `POST /base-rates`
- `GET /base-rates`
- `POST /product-spreads`
- `GET /product-spreads`
- `POST /fx-sync`
- `GET /conversions`

There is no `/product-types` management API in the implemented surface.

### Assignors and receivables

- `POST /assignors`
- `GET /assignors`
- `GET /assignors/{id}`
- `POST /receivables`
- `GET /receivables`
- `GET /receivables/{id}`

### Pricing

- `POST /pricing-simulations` (non-persisting)
- `POST /pricing-quotes`
- `GET /pricing-quotes/{id}`

### Settlement

- `POST /settlement-previews` (non-persisting)
- `POST /settlements`
- `GET /settlements/{id}`
- `POST /settlements/{id}/reversals`

`POST /settlements` and `POST /settlements/{id}/reversals` require `Idempotency-Key`.

### Reporting

- `GET /settlement-statements`

Supported parameters:

- `from`
- `to`
- `assignorId`
- `assetCurrency`
- `settlementCurrency`
- `productType`
- `page`
- `size`

Results use a fixed deterministic descending order. Pagination is server-side with a capped page size. Cursor pagination is documented as the high-scale evolution; offset pagination is sufficient for the exercise UI.

### Audit

- `GET /audit-events`

The audit endpoint accepts only a bounded `size` parameter from 1 through 100.

### Error contract

Errors use `application/problem+json` based on RFC 9457 and include:

- type
- title
- status
- detail
- instance
- error code
- correlation ID
- field violations when applicable

---

## 7. Data model

Core tables:

- `users`
- `user_roles`
- `assignors`
- `currencies`
- `product_types`
- `product_spread_versions`
- `base_rate_versions`
- `exchange_rates`
- `runtime_fixture_records` (review fixture profiles only)
- `receivables`
- `pricing_quotes`
- `settlements`
- `settlement_items`
- `idempotency_records`
- `settlement_reversals`
- `audit_events`

Important constraints and indexes:

- Unique currency code
- Unique product code
- Unique settlement item by receivable ID
- Unique idempotency key scoped to operation/client
- Positive amounts and rates through checks
- Indexed FX pair and observation timestamp
- Indexed statement date, assignor, product, and currencies
- Optimistic version on receivables
- Foreign keys for all financial relationships
- PostgreSQL immutability triggers protect exchange-rate, quote, settlement, settlement-item, reversal, and audit history; quote lifecycle permits only `ACTIVE` → `CONSUMED` without changing snapshot values.
- Flyway migrations V1–V23 are the schema authority; [`architecture/er-diagram.mmd`](architecture/er-diagram.mmd) mirrors those tables and the derived ledger identity.

---

## 8. Acceptance criteria by capability

All capability criteria below are implemented locally and mapped to executable commands and artifacts in [`REQUIREMENT_TRACEABILITY.md`](REQUIREMENT_TRACEABILITY.md). The representative reporting plan uses 10,000 PostgreSQL rows and is evidence of query shape, not production throughput.

### Currency engine

- An admin can register a valid historical FX rate.
- Invalid, zero, negative, or unsupported rates are rejected.
- Pricing deterministically selects the correct non-stale rate.
- External-provider failures use timeout, bounded retry, and circuit-breaker policies.

### Pricing

- Each product selects its own strategy.
- Formula, fractional term, conversion, precision, and rounding are covered by deterministic unit tests.
- Same-currency quotes do not require FX.
- Cross-currency quotes preserve the selected FX snapshot.
- Unsupported products, stale rates, and past-due receivables are rejected.

### Settlement

- All items commit or all items roll back.
- Concurrent settlement attempts cannot both succeed.
- Repeated equivalent idempotent requests return the same settlement.
- Expired quotes and altered receivable state are rejected.
- Completed records retain every value needed to reproduce the decision.

### Reporting

- Authorized users can filter by date, assignor, product, and currency.
- Results are deterministically sorted and server-side paginated.
- Parameterized, indexable reporting filters and stable server-side pagination are exercised on representative PostgreSQL data; PostgreSQL may select sequential scans when its cost model estimates them cheaper.
- Reports do not trigger per-row database queries.

### Frontend

- Users can log in and only see authorized actions.
- Operators can register a receivable, obtain a quote, review its breakdown, and settle it.
- Statement filters and pagination are reflected in URL state.
- Loading, empty, validation, authorization, stale quote, and server-error states are visible and actionable.

### Operational quality

- The system starts through Docker Compose.
- CI runs formatting/linting, unit tests, integration tests, frontend tests, and builds.
- Health and readiness endpoints distinguish application and dependency health.
- Logs are structured and include correlation IDs.
- Prometheus metrics expose request, pricing, settlement, FX, and error measurements.

---

## 9. Testing strategy

### Backend

1. Domain unit tests for money, terms, strategies, rounding, and conversion
2. Application tests for use-case orchestration and authorization
3. Testcontainers integration tests against PostgreSQL
4. Concurrency tests for double settlement
5. API tests for contracts and error semantics
6. Architecture tests for module/layer boundaries
7. Query-plan or representative-volume tests for reporting

### Frontend

1. Unit tests for pure formatting and state logic
2. Component tests for forms, quote breakdowns, errors, and permissions
3. Mock-server integration tests for API workflows
4. Playwright smoke test for login → receivable → quote → settlement → statement

Testing emphasizes the critical financial path rather than maximizing raw coverage percentage.

---

## 10. Observability and resilience

- JSON structured logs
- Correlation ID accepted or generated per request
- Audit events separate from operational logs
- Micrometer metrics exported for Prometheus
- Timers for quote, settlement, report, and FX-provider calls
- Counters for rejected pricing, settlement conflicts, stale rates, and external failures
- Spring Boot Actuator liveness and readiness endpoints
- Timeout, exponential backoff with jitter, and circuit breaker for external FX calls
- No automatic retry around settlement creation
- Sensitive values and credentials are excluded from logs

Grafana dashboards may be provisioned if time permits; metric definitions are mandatory.

---

## 11. Staff-level scale evolution

The implemented modular monolith remains the source of truth for the exercise. The root README documents a quantitative **proposed** evolution toward 1 million transactions per minute (16,667/s sustained, 33,334/s at the stated 2× peak assumption), including partition/shard starting points, SLOs, ownership, and DR targets. No throughput, capacity, or production-scale proof is claimed:

- Separate command ingestion from synchronous processing.
- Partition events and transactional data by tenant/assignor and time.
- Use an append-only event log or outbox with Kafka-compatible streaming.
- Preserve idempotency at ingress and consumer boundaries.
- Materialize read models for statements.
- Cache versioned reference/rate data.
- Use database partitioning and eventually sharding.
- Keep settlement consistency strong within a partition.
- Use eventual consistency for analytics and non-authoritative projections.
- Apply backpressure, dead-letter handling, replay, and reconciliation.
- Define SLOs, capacity models, disaster recovery, and operational ownership before decomposition.

Microservice boundaries would be extracted only when measured scale or team ownership justifies them; none is implemented here.

---

## 12. Ordered delivery plan

Milestones 0–6 are implemented locally with evidence in [`REQUIREMENT_TRACEABILITY.md`](REQUIREMENT_TRACEABILITY.md). Milestone 7's local operations, documentation, security, and crisis/revert evidence is implemented. A historical local annotated `v1.0.0` already points to `af898ef`; it predates the current remediation and has not been moved or reused. No remote is configured, so hosted collaboration/publication and any new release tag remain blocked pending explicit human authorization of the exact reviewed SHA and a new version.

### Milestone 0 — Specification and architecture

- Finalize glossary and assumptions
- Create ADRs
- Create C4 diagrams
- Define API contracts and error model
- Define ER model and migration plan

### Milestone 1 — Engineering foundation

- Initialize backend and frontend
- Configure PostgreSQL and Docker Compose
- Configure formatting, linting, tests, hooks, and CI
- Add health endpoints and baseline documentation

### Milestone 2 — Identity and reference data

- Implement users, login, JWT, roles, and audit context
- Implement currencies, products, spreads, and base rates
- Implement exchange-rate persistence and provider abstraction

### Milestone 3 — Receivables and pricing

- Implement assignors and receivables
- Implement money and rate value objects
- Implement strategy-based pricing
- Implement FX conversion and persisted quotes
- Complete pricing test matrix

### Milestone 4 — Settlement integrity

- Implement batch settlement
- Implement idempotency
- Implement optimistic locking and uniqueness guarantees
- Add rollback and concurrency integration tests
- Add reversals if the primary path is complete

### Milestone 5 — Reporting

- Implement optimized statement query
- Add filtering, sorting, pagination, and authorization
- Verify indexes and representative query plans

### Milestone 6 — Operator frontend

- Implement authentication flow
- Implement receivable and quote workflow
- Implement settlement confirmation
- Implement statement grid and URL-backed filters
- Add critical UI and E2E tests

### Milestone 7 — Operational and staff artifacts

- Complete metrics, resilience, and structured logging
- Complete high-scale and EDA proposal
- Document Git workflow and crisis simulation
- Complete reviewer, AI-usage, and human/tooling documentation
- Run the local release checklist
- Preserve the historical local `v1.0.0`; publish or create a new version tag only after the Prompt 12 authorization gates and exact reviewed-SHA approval

---

## 13. Canonical SDD execution sequence

The delivery plan is implemented through the 12 independently verifiable prompts in [`docs/sdd/README.md`](./sdd/README.md). The 12-prompt suite replaces the earlier 28-item decomposition and is the canonical source for ordering, review gates, contracts, acceptance criteria, verification commands, and commit outcomes.
