# SRM Credit Engine — Requirements and Delivery Plan

## 1. Delivery target

The implementation will demonstrate **senior-level engineering depth** while including selected **staff-level architecture and governance artifacts**.

The objective is not to build a distributed production platform in 3–4 days. It is to deliver a robust modular monolith, prove the critical financial paths, and document how it would evolve to very high scale.

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
- Spring Data JPA for transactional write models
- jOOQ or Spring JDBC for reporting queries
- PostgreSQL
- Flyway migrations
- Bean Validation
- springdoc-openapi
- Resilience4j
- Micrometer with Prometheus registry
- JUnit 5, AssertJ, Mockito, Testcontainers, REST Assured

### Frontend

- React with TypeScript
- Vite
- React Router
- TanStack Query for server state
- React Hook Form and Zod
- TanStack Table for server-side grids
- Vitest and React Testing Library
- Playwright for a small critical-path E2E suite

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
- A settlement stores snapshots of the base rate, spread, and strategy version used.

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
- A pricing simulation creates a persisted quote for auditability.
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

The exercise will implement local authentication using Spring Security.

### Authentication

- Users are stored in PostgreSQL.
- Passwords are hashed with Argon2 or BCrypt.
- Login returns a short-lived signed JWT access token.
- Access token lifetime: 15 minutes.
- Refresh tokens are omitted unless time remains; users can authenticate again.
- A seeded development operator is available only through local environment configuration.

### Roles

- `OPERATOR`: create receivables, request quotes, and create settlements
- `ANALYST`: read statements, receivables, quotes, and settlements
- `ADMIN`: manage exchange rates, base rates, product configurations, and users
- `AUDITOR`: read all financial and audit records without mutation rights

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
- JPA supports transactional write paths.
- Reporting bypasses domain reconstruction and uses read-optimized SQL/jOOQ.
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
- `GET /product-types`

### Assignors and receivables

- `POST /assignors`
- `GET /assignors`
- `POST /receivables`
- `GET /receivables/{id}`

### Pricing

- `POST /pricing-quotes`
- `GET /pricing-quotes/{id}`

### Settlement

- `POST /settlements`
- `GET /settlements/{id}`

`POST /settlements` requires `Idempotency-Key`.

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
- `sort`

Pagination is server-side with a capped page size. Cursor pagination is documented as the high-scale evolution; offset pagination is sufficient for the exercise UI.

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

---

## 8. Acceptance criteria by capability

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
- Query plans use the intended indexes on representative data.
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

The implemented modular monolith remains the source of truth for the exercise. The architecture document will explain an evolution toward 1 million transactions per minute:

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

Microservice boundaries will be extracted only when scaling or team ownership justifies them.

---

## 12. Ordered delivery plan

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
- Complete `README.md` and `AI_USAGE.md`
- Run release checklist and tag `v1.0.0`

---

## 13. Planned SDD prompt sequence

The plan should later be split into small, independently verifiable prompts:

1. Repository and architecture foundation
2. Backend build, quality gates, and module boundaries
3. Local PostgreSQL and Flyway foundation
4. Error contract, correlation, and API conventions
5. Authentication and role authorization
6. Currency, product, spread, and base-rate reference data
7. Exchange-rate storage and manual API
8. Mock FX provider with resilience policies
9. Assignor and receivable domain
10. Money, rate, term, and rounding primitives
11. Pricing strategy contract and mercantile invoice strategy
12. Post-dated cheque strategy
13. Cross-currency conversion
14. Persisted, expiring pricing quotes
15. Atomic batch settlement
16. Settlement idempotency
17. Optimistic locking and concurrency tests
18. Settlement reversal and audit trail
19. Optimized settlement statement query
20. Frontend foundation and generated API integration
21. Frontend authentication and authorization
22. Operator receivable and quote workflow
23. Settlement confirmation workflow
24. Statement grid and filters
25. Structured logs, metrics, and health checks
26. End-to-end and performance validation
27. C4, ADRs, high-scale design, and EDA proposal
28. README, AI usage report, Git evidence, and release checklist

Each prompt must specify context, exact scope, non-goals, contracts, acceptance criteria, tests, verification commands, documentation updates, and an atomic Conventional Commit message.
